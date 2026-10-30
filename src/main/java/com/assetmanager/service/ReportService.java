package com.assetmanager.service;

import com.assetmanager.model.*;
import com.assetmanager.repository.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Builds every report as a simple table (headers + rows) so one template and one CSV exporter serve all reports. */
@Service
public class ReportService {
    public record ReportData(String title, List<String> headers, List<List<String>> rows) {}
    public static final Map<String, String> TYPES = new LinkedHashMap<>();
    static {
        TYPES.put("summary", "Asset Summary"); TYPES.put("category", "Category Report");
        TYPES.put("financial", "Financial Report"); TYPES.put("maintenance", "Maintenance Report");
        TYPES.put("repair", "Repair Report"); TYPES.put("warranty", "Warranty Report");
        TYPES.put("lifecycle", "Asset Lifecycle Report");
    }

    private final AssetRepository assetRepo;
    private final MaintenanceRepository maintRepo;
    private final RepairRepository repairRepo;
    private final UpgradeRepository upgradeRepo;
    private final WarrantyRepository warrantyRepo;
    private final HistoryRepository historyRepo;
    private final AssetService assetService;
    private final CalcService calc;

    public ReportService(AssetRepository assetRepo, MaintenanceRepository maintRepo, RepairRepository repairRepo,
                         UpgradeRepository upgradeRepo, WarrantyRepository warrantyRepo, HistoryRepository historyRepo,
                         AssetService assetService, CalcService calc) {
        this.assetRepo = assetRepo; this.maintRepo = maintRepo; this.repairRepo = repairRepo; this.upgradeRepo = upgradeRepo;
        this.warrantyRepo = warrantyRepo; this.historyRepo = historyRepo; this.assetService = assetService; this.calc = calc;
    }

    private static String money(BigDecimal v) { return String.format("%,.2f", AssetService.nz(v)); }
    private static boolean inRange(LocalDate d, LocalDate from, LocalDate to) {
        return d != null && (from == null || !d.isBefore(from)) && (to == null || !d.isAfter(to));
    }
    private static boolean catOk(Asset a, String cat) { return cat == null || cat.isBlank() || cat.equals(a.getCategory()); }

    public ReportData build(String type, Long uid, LocalDate from, LocalDate to, String category, Long assetId) {
        LocalDate today = LocalDate.now();
        List<Asset> assets = assetRepo.findByUserIdOrderByNameAsc(uid);
        List<List<String>> rows = new ArrayList<>();
        String title = TYPES.getOrDefault(type, TYPES.get("summary"));
        List<String> h;

        switch (type) {
            case "category" -> {
                h = List.of("Category", "Assets", "Total Purchase Value", "Current Estimated Value");
                Map<String, List<Asset>> grouped = new TreeMap<>();
                for (Asset a : assets) if (catOk(a, category) && (from == null && to == null || inRange(a.getPurchaseDate(), from, to)))
                    grouped.computeIfAbsent(a.getCategory(), k -> new ArrayList<>()).add(a);
                grouped.forEach((cat, list) -> {
                    BigDecimal p = BigDecimal.ZERO, c = BigDecimal.ZERO;
                    for (Asset a : list) { p = p.add(a.getPurchasePrice()); c = c.add(assetService.currentValue(a)); }
                    rows.add(List.of(cat, String.valueOf(list.size()), money(p), money(c)));
                });
            }
            case "financial" -> {
                h = List.of("Measure", "Amount (INR)");
                BigDecimal p = BigDecimal.ZERO, c = BigDecimal.ZERO, m = BigDecimal.ZERO, r = BigDecimal.ZERO, u = BigDecimal.ZERO;
                for (Asset a : assets) {
                    if (!catOk(a, category) || !(from == null && to == null || inRange(a.getPurchaseDate(), from, to))) continue;
                    var cs = assetService.costs(a);
                    p = p.add(cs.purchase()); m = m.add(cs.maintenance()); r = r.add(cs.repair()); u = u.add(cs.upgrade());
                    c = c.add(assetService.currentValue(a));
                }
                rows.add(List.of("Total purchase value", money(p))); rows.add(List.of("Current estimated value", money(c)));
                rows.add(List.of("Total maintenance expenses", money(m))); rows.add(List.of("Total repair expenses", money(r)));
                rows.add(List.of("Total upgrade expenses", money(u)));
                rows.add(List.of("Total lifecycle cost", money(p.add(m).add(r).add(u))));
            }
            case "maintenance" -> {
                h = List.of("Date", "Asset", "Type", "Provider", "Cost", "Next Due");
                for (MaintenanceRecord x : maintRepo.findByAssetUserIdOrderByMaintenanceDateDesc(uid))
                    if (catOk(x.getAsset(), category) && (from == null && to == null || inRange(x.getMaintenanceDate(), from, to)))
                        rows.add(List.of(x.getMaintenanceDate().toString(), x.getAsset().getName(), x.getMaintenanceType(),
                                nvl(x.getProvider()), money(x.getCost()), x.getNextMaintenanceDate() == null ? "-" : x.getNextMaintenanceDate().toString()));
            }
            case "repair" -> {
                h = List.of("Date", "Asset", "Problem", "Status", "Provider", "Cost");
                for (RepairRecord x : repairRepo.findByAssetUserIdOrderByRepairDateDesc(uid))
                    if (catOk(x.getAsset(), category) && (from == null && to == null || inRange(x.getRepairDate(), from, to)))
                        rows.add(List.of(x.getRepairDate().toString(), x.getAsset().getName(), x.getProblemDescription(),
                                x.getStatus(), nvl(x.getProvider()), money(x.getCost())));
            }
            case "warranty" -> {
                h = List.of("Asset", "Provider", "Start", "End", "Status", "Days Remaining");
                for (WarrantyRecord w : warrantyRepo.findByAssetUserId(uid)) {
                    if (!catOk(w.getAsset(), category)) continue;
                    var s = calc.dateStatus(w.getEndDate(), today);
                    rows.add(List.of(w.getAsset().getName(), nvl(w.getProvider()), w.getStartDate().toString(), w.getEndDate().toString(),
                            s.status(), String.valueOf(s.days())));
                }
            }
            case "lifecycle" -> {
                h = List.of("Time", "Asset", "Event", "Description");
                DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                for (LifecycleHistory x : historyRepo.findByAssetUserIdOrderByEventTimeDesc(uid))
                    if (catOk(x.getAsset(), category) && (assetId == null || assetId.equals(x.getAsset().getId()))
                            && (from == null && to == null || inRange(x.getEventTime().toLocalDate(), from, to)))
                        rows.add(List.of(x.getEventTime().format(f), x.getAsset().getName(), x.getEventType(), x.getDescription()));
            }
            default -> {   // summary
                h = List.of("ID", "Name", "Category", "Brand", "Purchase Date", "Purchase Price", "Current Value", "Status", "Health");
                for (Asset a : assets) {
                    if (!catOk(a, category) || !(from == null && to == null || inRange(a.getPurchaseDate(), from, to))) continue;
                    rows.add(List.of(String.valueOf(a.getId()), a.getName(), a.getCategory(), nvl(a.getBrand()), a.getPurchaseDate().toString(),
                            money(a.getPurchasePrice()), money(assetService.currentValue(a)), a.getStatus(),
                            "DISPOSED".equals(a.getStatus()) ? "-" : assetService.healthOf(a).score() + "/100"));
                }
            }
        }
        return new ReportData(title, h, rows);
    }

    private static String nvl(String s) { return s == null || s.isBlank() ? "-" : s; }

    /** Turns a report into CSV text. Values are quoted so commas inside text are safe. */
    public String toCsv(ReportData r) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", r.headers().stream().map(ReportService::q).toList())).append("\n");
        for (List<String> row : r.rows()) sb.append(String.join(",", row.stream().map(ReportService::q).toList())).append("\n");
        return sb.toString();
    }
    private static String q(String s) {
        String v = s == null ? "" : s;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0 && !v.matches("-?[\\d,.]+")) v = "'" + v;   // block CSV formula injection
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
