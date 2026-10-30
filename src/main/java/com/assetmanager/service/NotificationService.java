package com.assetmanager.service;

import com.assetmanager.model.*;
import com.assetmanager.repository.*;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Builds notifications on the fly from real database records (nothing is stored). */
@Service
public class NotificationService {
    public record Notification(String type, String severity, String message, Long assetId) {}

    private final AssetRepository assetRepo;
    private final WarrantyRepository warrantyRepo;
    private final InsuranceRepository insuranceRepo;
    private final MaintenanceRepository maintRepo;
    private final RepairRepository repairRepo;
    private final AssetService assetService;
    private final CalcService calc;

    public NotificationService(AssetRepository assetRepo, WarrantyRepository warrantyRepo, InsuranceRepository insuranceRepo,
                               MaintenanceRepository maintRepo, RepairRepository repairRepo, AssetService assetService, CalcService calc) {
        this.assetRepo = assetRepo; this.warrantyRepo = warrantyRepo; this.insuranceRepo = insuranceRepo;
        this.maintRepo = maintRepo; this.repairRepo = repairRepo; this.assetService = assetService; this.calc = calc;
    }

    public List<Notification> build(Long userId) {
        LocalDate today = LocalDate.now();
        List<Notification> list = new ArrayList<>();

        for (WarrantyRecord w : warrantyRepo.findByAssetUserId(userId)) {
            if ("DISPOSED".equals(w.getAsset().getStatus())) continue;
            var s = calc.dateStatus(w.getEndDate(), today);
            if ("EXPIRING_SOON".equals(s.status()))
                list.add(new Notification("WARRANTY", "warning", "Warranty for " + w.getAsset().getName() + " expires in " + s.days() + " day(s)", w.getAsset().getId()));
        }
        for (InsuranceRecord i : insuranceRepo.findByAssetUserId(userId)) {
            if ("DISPOSED".equals(i.getAsset().getStatus())) continue;
            var s = calc.dateStatus(i.getEndDate(), today);
            if ("EXPIRING_SOON".equals(s.status()))
                list.add(new Notification("INSURANCE", "warning", "Insurance for " + i.getAsset().getName() + " expires in " + s.days() + " day(s)", i.getAsset().getId()));
        }
        for (Asset a : assetRepo.findByUserIdAndStatusNotOrderByNameAsc(userId, "DISPOSED")) {
            List<MaintenanceRecord> maint = maintRepo.findByAssetIdOrderByMaintenanceDateDesc(a.getId());
            LocalDate next = maint.stream().map(MaintenanceRecord::getNextMaintenanceDate).filter(d -> d != null)
                    .max(LocalDate::compareTo).orElse(null);
            if (next != null) {
                long d = ChronoUnit.DAYS.between(today, next);
                if (d < 0) list.add(new Notification("MAINTENANCE", "danger", "Maintenance for " + a.getName() + " is overdue by " + (-d) + " day(s)", a.getId()));
                else if (d <= 7) list.add(new Notification("MAINTENANCE", "warning", "Maintenance for " + a.getName() + " is due in " + d + " day(s)", a.getId()));
            }
            long open = repairRepo.countByAssetIdAndStatusNot(a.getId(), "COMPLETED");
            if (open > 0) list.add(new Notification("REPAIR", "danger", open + " pending repair(s) for " + a.getName(), a.getId()));
            if ("NEEDS_ATTENTION".equals(assetService.healthOf(a).status()))
                list.add(new Notification("ATTENTION", "danger", a.getName() + " needs attention (low health score)", a.getId()));
        }
        return list;
    }
}
