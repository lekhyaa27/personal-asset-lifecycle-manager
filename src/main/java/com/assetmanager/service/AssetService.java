package com.assetmanager.service;

import com.assetmanager.exception.ResourceNotFoundException;
import com.assetmanager.model.*;
import com.assetmanager.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Business logic for assets and everything attached to them. Controllers call this class. */
@Service
public class AssetService {
    public static final List<String> STATUSES = List.of("ACTIVE", "UNDER_MAINTENANCE", "UNDER_REPAIR", "DISPOSED");
    public static final List<String> REPAIR_STATUSES = List.of("PENDING", "IN_PROGRESS", "COMPLETED");

    public record Costs(BigDecimal purchase, BigDecimal maintenance, BigDecimal repair, BigDecimal upgrade, BigDecimal total) {}

    private final UserRepository userRepo;
    private final AssetRepository assetRepo;
    private final MaintenanceRepository maintRepo;
    private final RepairRepository repairRepo;
    private final UpgradeRepository upgradeRepo;
    private final WarrantyRepository warrantyRepo;
    private final InsuranceRepository insuranceRepo;
    private final HistoryRepository historyRepo;
    private final CalcService calc;

    public AssetService(UserRepository userRepo, AssetRepository assetRepo, MaintenanceRepository maintRepo,
                        RepairRepository repairRepo, UpgradeRepository upgradeRepo, WarrantyRepository warrantyRepo,
                        InsuranceRepository insuranceRepo, HistoryRepository historyRepo, CalcService calc) {
        this.userRepo = userRepo; this.assetRepo = assetRepo; this.maintRepo = maintRepo; this.repairRepo = repairRepo;
        this.upgradeRepo = upgradeRepo; this.warrantyRepo = warrantyRepo; this.insuranceRepo = insuranceRepo;
        this.historyRepo = historyRepo; this.calc = calc;
    }

    public static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    // ---------- reading ----------
    /** The security check: returns the asset only if it belongs to this user. */
    public Asset getOwned(Long assetId, Long userId) {
        return assetRepo.findByIdAndUserId(assetId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));
    }

    public List<Asset> activeAssets(Long userId) {
        return assetRepo.findByUserIdAndStatusNotOrderByNameAsc(userId, "DISPOSED");
    }

    public BigDecimal currentValue(Asset a) {
        if ("DISPOSED".equals(a.getStatus())) return BigDecimal.ZERO;
        return calc.depreciation(a, LocalDate.now()).current();
    }

    public Costs costs(Asset a) {
        BigDecimal m = nz(maintRepo.totalByAsset(a.getId()));
        BigDecimal r = nz(repairRepo.totalByAsset(a.getId()));
        BigDecimal u = nz(upgradeRepo.totalByAsset(a.getId()));
        BigDecimal p = nz(a.getPurchasePrice());
        return new Costs(p, m, r, u, p.add(m).add(r).add(u));
    }

    public CalcService.Health healthOf(Asset a) {
        LocalDate today = LocalDate.now();
        String ws = calc.dateStatus(warrantyRepo.findByAssetId(a.getId()).map(WarrantyRecord::getEndDate).orElse(null), today).status();
        return calc.health(a, repairRepo.findByAssetIdOrderByRepairDateDesc(a.getId()),
                maintRepo.findByAssetIdOrderByMaintenanceDateDesc(a.getId()), ws, today);
    }

    private static String newToken() { return java.util.UUID.randomUUID().toString().replace("-", ""); }

    /** Gives older assets (created before this feature) a QR token the first time it is needed. */
    @Transactional
    public String ensureToken(Asset a) {
        if (a.getPublicToken() == null) { a.setPublicToken(newToken()); assetRepo.save(a); }
        return a.getPublicToken();
    }

    // ---------- asset create / update / status ----------
    @Transactional
    public Asset register(Long userId, AssetForm f) {
        User user = userRepo.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Asset a = new Asset();
        a.setUser(user);
        apply(a, f);
        a.setStatus("ACTIVE");
        a.setPublicToken(newToken());
        assetRepo.save(a);
        log(a, "REGISTERED", "Asset registered: " + a.getName(), null, "ACTIVE");
        if (f.getWarrantyEndDate() != null && f.getWarrantyStartDate() != null) {
            saveWarranty(a, f.getWarrantyProvider() != null && !f.getWarrantyProvider().isBlank() ? f.getWarrantyProvider() : a.getBrand(),
                    f.getWarrantyStartDate(), f.getWarrantyEndDate());
        }
        if (f.getInsuranceEndDate() != null && f.getInsuranceStartDate() != null) {
            saveInsurance(a, f.getInsuranceProvider(), f.getInsurancePolicyNumber(), f.getInsuranceStartDate(), f.getInsuranceEndDate());
        }
        return a;
    }

    @Transactional
    public Asset update(Long assetId, Long userId, AssetForm f) {
        Asset a = getOwned(assetId, userId);
        apply(a, f);
        assetRepo.save(a);
        log(a, "UPDATED", "Asset details edited", a.getStatus(), a.getStatus());
        return a;
    }

    private void apply(Asset a, AssetForm f) {
        a.setName(f.getName().trim()); a.setCategory(f.getCategory()); a.setBrand(f.getBrand()); a.setModel(f.getModel());
        a.setSerialNumber(f.getSerialNumber()); a.setPurchasePrice(f.getPurchasePrice()); a.setPurchaseDate(f.getPurchaseDate());
        a.setPurchaseLocation(f.getPurchaseLocation()); a.setAssetCondition(f.getAssetCondition());
        a.setUsefulLifeYears(f.getUsefulLifeYears()); a.setSalvageValue(f.getSalvageValue());
        a.setDescription(f.getDescription()); a.setNotes(f.getNotes());
    }

    @Transactional
    public void changeStatus(Asset a, String newStatus, String note) {
        if (!STATUSES.contains(newStatus)) throw new IllegalArgumentException("Invalid status");
        String old = a.getStatus();
        if (old.equals(newStatus)) return;
        a.setStatus(newStatus);
        if ("DISPOSED".equals(newStatus)) a.setDisposedDate(LocalDate.now()); else a.setDisposedDate(null);
        assetRepo.save(a);
        String type = "DISPOSED".equals(newStatus) ? "DISPOSED" : "STATUS_CHANGE";
        log(a, type, "Status changed from " + old + " to " + newStatus + (note == null || note.isBlank() ? "" : " - " + note), old, newStatus);
    }

    @Transactional
    public void changeStatus(Long assetId, Long userId, String newStatus, String note) {   // overloading example
        changeStatus(getOwned(assetId, userId), newStatus, note);
    }

    /** "Delete" = soft delete. The asset becomes DISPOSED so its history is kept. */
    @Transactional
    public void dispose(Long assetId, Long userId) {
        changeStatus(getOwned(assetId, userId), "DISPOSED", "Asset disposed / removed by user");
    }

    public void log(Asset a, String type, String desc, String oldS, String newS) {
        LifecycleHistory h = new LifecycleHistory();
        h.setAsset(a); h.setEventType(type); h.setDescription(desc); h.setOldStatus(oldS); h.setNewStatus(newS);
        historyRepo.save(h);
    }

    // ---------- maintenance / repair / upgrade ----------
    @Transactional
    public void addMaintenance(Long userId, Long assetId, MaintenanceRecord rec, boolean markUnderMaintenance) {
        Asset a = getOwned(assetId, userId);
        if (rec.getNextMaintenanceDate() != null && rec.getNextMaintenanceDate().isBefore(rec.getMaintenanceDate()))
            throw new IllegalArgumentException("Next maintenance date cannot be before the maintenance date");
        rec.setId(null); rec.setAsset(a);
        maintRepo.save(rec);
        log(a, "MAINTENANCE", rec.getMaintenanceType() + " maintenance recorded (cost " + rec.getCost() + ")", a.getStatus(), a.getStatus());
        if (markUnderMaintenance) changeStatus(a, "UNDER_MAINTENANCE", "Maintenance in progress");
    }

    @Transactional
    public void addRepair(Long userId, Long assetId, RepairRecord rec, String newCondition) {
        Asset a = getOwned(assetId, userId);
        if (!REPAIR_STATUSES.contains(rec.getStatus())) rec.setStatus("PENDING");
        if (rec.getCompletionDate() != null && rec.getCompletionDate().isBefore(rec.getRepairDate()))
            throw new IllegalArgumentException("Completion date cannot be before the repair date");
        if ("COMPLETED".equals(rec.getStatus()) && rec.getCompletionDate() == null) rec.setCompletionDate(LocalDate.now());
        rec.setId(null); rec.setAsset(a);
        repairRepo.save(rec);
        log(a, "REPAIR", "Repair (" + rec.getStatus() + "): " + rec.getProblemDescription(), a.getStatus(), a.getStatus());
        if (newCondition != null && !newCondition.isBlank()) { a.setAssetCondition(newCondition); assetRepo.save(a); }
        if ("COMPLETED".equals(rec.getStatus())) reactivateIfNoOpenRepairs(a);
        else changeStatus(a, "UNDER_REPAIR", "Repair " + rec.getStatus());
    }

    @Transactional
    public void completeRepair(Long userId, Long repairId) {
        RepairRecord r = repairRepo.findByIdAndAssetUserId(repairId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Repair not found"));
        r.setStatus("COMPLETED");
        if (r.getCompletionDate() == null) r.setCompletionDate(LocalDate.now());
        repairRepo.save(r);
        log(r.getAsset(), "REPAIR", "Repair completed: " + r.getProblemDescription(), r.getAsset().getStatus(), r.getAsset().getStatus());
        reactivateIfNoOpenRepairs(r.getAsset());
    }

    private void reactivateIfNoOpenRepairs(Asset a) {
        if ("UNDER_REPAIR".equals(a.getStatus()) && repairRepo.countByAssetIdAndStatusNot(a.getId(), "COMPLETED") == 0)
            changeStatus(a, "ACTIVE", "Repair completed");
    }

    @Transactional
    public void saveUpgrade(Long userId, Long assetId, UpgradeRecord form) {
        Asset a = getOwned(assetId, userId);
        UpgradeRecord rec;
        boolean isNew = form.getId() == null;
        if (isNew) rec = new UpgradeRecord();
        else rec = upgradeRepo.findByIdAndAssetUserId(form.getId(), userId).orElseThrow(() -> new ResourceNotFoundException("Upgrade not found"));
        rec.setAsset(a); rec.setUpgradeDate(form.getUpgradeDate()); rec.setDescription(form.getDescription());
        rec.setCost(form.getCost()); rec.setProvider(form.getProvider()); rec.setNotes(form.getNotes());
        upgradeRepo.save(rec);
        log(a, "UPGRADE", (isNew ? "Upgrade added: " : "Upgrade edited: ") + rec.getDescription() + " (cost " + rec.getCost() + ")", a.getStatus(), a.getStatus());
    }

    // ---------- warranty / insurance ----------
    @Transactional
    public void saveWarranty(Long userId, Long assetId, String provider, LocalDate start, LocalDate end) {
        saveWarranty(getOwned(assetId, userId), provider, start, end);
    }

    private void saveWarranty(Asset a, String provider, LocalDate start, LocalDate end) {
        if (end.isBefore(start)) throw new IllegalArgumentException("Warranty end date cannot be before the start date");
        WarrantyRecord w = warrantyRepo.findByAssetId(a.getId()).orElseGet(WarrantyRecord::new);
        boolean isNew = w.getId() == null;
        w.setAsset(a); w.setProvider(provider); w.setStartDate(start); w.setEndDate(end);
        warrantyRepo.save(w);
        log(a, "WARRANTY", (isNew ? "Warranty added" : "Warranty updated") + " (valid until " + end + ")", a.getStatus(), a.getStatus());
    }

    @Transactional
    public void saveInsurance(Long userId, Long assetId, String provider, String policy, LocalDate start, LocalDate end) {
        saveInsurance(getOwned(assetId, userId), provider, policy, start, end);
    }

    private void saveInsurance(Asset a, String provider, String policy, LocalDate start, LocalDate end) {
        if (end.isBefore(start)) throw new IllegalArgumentException("Insurance end date cannot be before the start date");
        InsuranceRecord i = insuranceRepo.findByAssetId(a.getId()).orElseGet(InsuranceRecord::new);
        boolean isNew = i.getId() == null;
        i.setAsset(a); i.setProvider(provider); i.setPolicyNumber(policy); i.setStartDate(start); i.setEndDate(end);
        insuranceRepo.save(i);
        log(a, "INSURANCE", (isNew ? "Insurance added" : "Insurance updated") + " (valid until " + end + ")", a.getStatus(), a.getStatus());
    }
}
