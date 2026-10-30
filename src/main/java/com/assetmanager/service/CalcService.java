package com.assetmanager.service;

import com.assetmanager.model.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * All the "business formulas" live here: depreciation, expiry status, health score, repair-or-replace.
 * It never touches the database, so it is easy to read and to unit-test.
 */
@Service
public class CalcService {

    public record Depreciation(BigDecimal annual, BigDecimal accumulated, BigDecimal current, String note) {}
    public record DateStatus(String status, Long days) {}
    public record Health(int score, String status, List<String> reasons) {}
    public record Recommendation(String verdict, List<String> reasons) {}

    /** Straight-line depreciation: annual = (price - salvage) / life. */
    public Depreciation depreciation(Asset a, LocalDate today) {
        BigDecimal price = a.getPurchasePrice() == null ? BigDecimal.ZERO : a.getPurchasePrice();
        Integer life = a.getUsefulLifeYears();
        if (life == null || life <= 0 || a.getPurchaseDate() == null) {
            return new Depreciation(BigDecimal.ZERO, BigDecimal.ZERO, price, "No useful life entered - depreciation not applied");
        }
        BigDecimal salvage = a.getSalvageValue() == null ? BigDecimal.ZERO : a.getSalvageValue();
        if (salvage.compareTo(price) > 0) salvage = price;                       // salvage cannot exceed price
        BigDecimal annual = price.subtract(salvage).divide(BigDecimal.valueOf(life), 2, RoundingMode.HALF_UP);
        double years = ChronoUnit.DAYS.between(a.getPurchaseDate(), today) / 365.25;
        years = Math.max(0, Math.min(years, life));                              // never beyond useful life
        BigDecimal accumulated = annual.multiply(BigDecimal.valueOf(years)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal current = price.subtract(accumulated).max(BigDecimal.ZERO);   // never negative
        return new Depreciation(annual, accumulated, current, "Straight-line method");
    }

    public double ageYears(Asset a, LocalDate today) {
        if (a.getPurchaseDate() == null) return 0;
        return Math.max(0, ChronoUnit.DAYS.between(a.getPurchaseDate(), today) / 365.25);
    }

    /** Used for warranty AND insurance: NOT_AVAILABLE, EXPIRED, EXPIRING_SOON (<= 30 days) or ACTIVE. */
    public DateStatus dateStatus(LocalDate end, LocalDate today) {
        if (end == null) return new DateStatus("NOT_AVAILABLE", null);
        long days = ChronoUnit.DAYS.between(today, end);
        if (days < 0) return new DateStatus("EXPIRED", days);
        if (days <= 30) return new DateStatus("EXPIRING_SOON", days);
        return new DateStatus("ACTIVE", days);
    }

    /** Rule-based health score out of 100. Every deduction is listed in "reasons". */
    public Health health(Asset a, List<RepairRecord> repairs, List<MaintenanceRecord> maint, String warrantyStatus, LocalDate today) {
        int score = 100;
        List<String> reasons = new ArrayList<>();
        double age = ageYears(a, today);
        Integer life = a.getUsefulLifeYears();

        if (life != null && life > 0) {
            double ratio = age / life;
            if (ratio >= 1) { score -= 30; reasons.add("Past its expected useful life (-30)"); }
            else if (ratio >= 0.75) { score -= 20; reasons.add("Used more than 75% of its useful life (-20)"); }
            else if (ratio >= 0.5) { score -= 10; reasons.add("Used more than 50% of its useful life (-10)"); }
            else reasons.add("Still early in its useful life (no deduction)");
        } else reasons.add("No useful life entered, so age is not scored");

        String cond = a.getAssetCondition() == null ? "GOOD" : a.getAssetCondition();
        switch (cond) {
            case "EXCELLENT" -> reasons.add("Condition is Excellent (no deduction)");
            case "GOOD" -> { score -= 5; reasons.add("Condition is Good (-5)"); }
            case "FAIR" -> { score -= 15; reasons.add("Condition is Fair (-15)"); }
            default -> { score -= 30; reasons.add("Condition is Poor (-30)"); }
        }

        if (!repairs.isEmpty()) {
            int p = Math.min(repairs.size() * 5, 20);
            score -= p; reasons.add(repairs.size() + " repair(s) recorded (-" + p + ")");
        }
        if (repairs.stream().anyMatch(r -> !"COMPLETED".equals(r.getStatus()))) {
            score -= 10; reasons.add("Repair still pending / in progress (-10)");
        }

        LocalDate nextDue = maint.stream().map(MaintenanceRecord::getNextMaintenanceDate)
                .filter(d -> d != null).max(LocalDate::compareTo).orElse(null);
        if (nextDue != null && nextDue.isBefore(today)) { score -= 10; reasons.add("Maintenance is overdue (-10)"); }
        else if (maint.isEmpty() && age > 1) { score -= 5; reasons.add("No maintenance recorded for an asset older than 1 year (-5)"); }
        else if (!maint.isEmpty()) reasons.add("Maintenance history exists (no deduction)");

        if ("EXPIRED".equals(warrantyStatus)) { score -= 5; reasons.add("Warranty has expired (-5)"); }

        score = Math.max(0, Math.min(100, score));
        String status = score >= 75 ? "GOOD" : score >= 50 ? "FAIR" : "NEEDS_ATTENTION";
        return new Health(score, status, reasons);
    }

    /** Simple rule-based advice (NOT artificial intelligence). */
    public Recommendation recommend(Asset a, BigDecimal currentValue, BigDecimal repairTotal, boolean pendingRepair, LocalDate today) {
        List<String> reasons = new ArrayList<>();
        double age = ageYears(a, today);
        Integer life = a.getUsefulLifeYears();
        boolean pastLife = life != null && life > 0 && age >= life;
        String cond = a.getAssetCondition() == null ? "GOOD" : a.getAssetCondition();
        BigDecimal base = currentValue.max(BigDecimal.ONE);
        double ratio = repairTotal.divide(base, 4, RoundingMode.HALF_UP).doubleValue();

        reasons.add(String.format("Total repair cost is %.0f%% of the current estimated value", ratio * 100));
        if (pastLife) reasons.add("Asset has reached the end of its expected useful life");
        reasons.add("Asset condition: " + cond);
        if (pendingRepair) reasons.add("There is an unfinished repair");

        String verdict;
        if (ratio >= 0.6 || (pastLife && "POOR".equals(cond)) || (pastLife && ratio >= 0.4)) verdict = "Consider replacement";
        else if (ratio >= 0.25 || pendingRepair || "POOR".equals(cond) || "FAIR".equals(cond)) verdict = "Repair recommended";
        else verdict = "Continue using";
        return new Recommendation(verdict, reasons);
    }
}
