package com.assetmanager.controller;

import com.assetmanager.model.Asset;
import com.assetmanager.model.WarrantyRecord;
import com.assetmanager.repository.*;
import com.assetmanager.service.AssetService;
import com.assetmanager.service.CalcService;
import com.assetmanager.service.NotificationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.SessionAttribute;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Controller
public class DashboardController {
    private final AssetRepository assetRepo; private final MaintenanceRepository maintRepo; private final RepairRepository repairRepo;
    private final WarrantyRepository warrantyRepo; private final HistoryRepository historyRepo;
    private final AssetService assetService; private final CalcService calc; private final NotificationService notifications;

    public DashboardController(AssetRepository assetRepo, MaintenanceRepository maintRepo, RepairRepository repairRepo,
                               WarrantyRepository warrantyRepo, HistoryRepository historyRepo, AssetService assetService,
                               CalcService calc, NotificationService notifications) {
        this.assetRepo = assetRepo; this.maintRepo = maintRepo; this.repairRepo = repairRepo; this.warrantyRepo = warrantyRepo;
        this.historyRepo = historyRepo; this.assetService = assetService; this.calc = calc; this.notifications = notifications;
    }

    @GetMapping("/dashboard")
    public String dashboard(@SessionAttribute("userId") Long uid, Model m) {
        List<Asset> all = assetRepo.findByUserIdOrderByNameAsc(uid);
        List<Asset> live = all.stream().filter(a -> !"DISPOSED".equals(a.getStatus())).toList();

        BigDecimal purchase = BigDecimal.ZERO, current = BigDecimal.ZERO;
        int attention = 0;
        Map<String, Long> byCategory = new LinkedHashMap<>(), byStatus = new LinkedHashMap<>();
        for (Asset a : all) {
            purchase = purchase.add(a.getPurchasePrice());
            byStatus.merge(a.getStatus(), 1L, Long::sum);
            if ("DISPOSED".equals(a.getStatus())) continue;
            current = current.add(assetService.currentValue(a));
            byCategory.merge(a.getCategory(), 1L, Long::sum);
            if ("NEEDS_ATTENTION".equals(assetService.healthOf(a).status())) attention++;
        }
        int underWarranty = 0;
        for (WarrantyRecord w : warrantyRepo.findByAssetUserId(uid)) {
            String s = calc.dateStatus(w.getEndDate(), LocalDate.now()).status();
            if (!"DISPOSED".equals(w.getAsset().getStatus()) && ("ACTIVE".equals(s) || "EXPIRING_SOON".equals(s))) underWarranty++;
        }

        List<String> topNames = all.stream().sorted((x, y) -> y.getPurchasePrice().compareTo(x.getPurchasePrice())).limit(8).map(Asset::getName).toList();
        List<BigDecimal> topValues = all.stream().sorted((x, y) -> y.getPurchasePrice().compareTo(x.getPurchasePrice())).limit(8).map(Asset::getPurchasePrice).toList();
        List<String> mLabels = new ArrayList<>(); List<BigDecimal> mValues = new ArrayList<>();
        for (Object[] row : maintRepo.costByAsset(uid)) { mLabels.add((String) row[0]); mValues.add((BigDecimal) row[1]); }

        m.addAttribute("totalAssets", all.size());
        m.addAttribute("activeAssets", live.stream().filter(a -> "ACTIVE".equals(a.getStatus())).count());
        m.addAttribute("purchaseValue", purchase);
        m.addAttribute("currentValue", current);
        m.addAttribute("maintTotal", AssetService.nz(maintRepo.totalByUser(uid)));
        m.addAttribute("repairTotal", AssetService.nz(repairRepo.totalByUser(uid)));
        m.addAttribute("underWarranty", underWarranty);
        m.addAttribute("attention", attention);
        m.addAttribute("catLabels", byCategory.keySet()); m.addAttribute("catValues", byCategory.values());
        m.addAttribute("statusLabels", byStatus.keySet()); m.addAttribute("statusValues", byStatus.values());
        m.addAttribute("topNames", topNames); m.addAttribute("topValues", topValues);
        m.addAttribute("maintLabels", mLabels); m.addAttribute("maintValues", mValues);
        m.addAttribute("activities", historyRepo.findTop10ByAssetUserIdOrderByEventTimeDesc(uid));
        m.addAttribute("notifs", notifications.build(uid));
        return "dashboard";
    }
}
