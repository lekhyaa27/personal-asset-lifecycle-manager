package com.assetmanager;

import com.assetmanager.model.Asset;
import com.assetmanager.service.CalcService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

/** Plain unit tests (no database needed). Run with: mvnw test  /  mvn test */
class CalcServiceTest {
    private final CalcService calc = new CalcService();

    private Asset asset(String price, String salvage, Integer life, LocalDate bought) {
        Asset a = new Asset();
        a.setPurchasePrice(new BigDecimal(price)); a.setSalvageValue(new BigDecimal(salvage));
        a.setUsefulLifeYears(life); a.setPurchaseDate(bought);
        return a;
    }

    @Test void annualDepreciationIsStraightLine() {
        var d = calc.depreciation(asset("60000", "10000", 5, LocalDate.now()), LocalDate.now());
        assertEquals(new BigDecimal("10000.00"), d.annual());
        assertEquals(0, d.accumulated().compareTo(BigDecimal.ZERO));
    }

    @Test void valueNeverDropsBelowSalvageAfterUsefulLife() {
        var d = calc.depreciation(asset("60000", "10000", 5, LocalDate.now().minusYears(20)), LocalDate.now());
        assertEquals(0, d.current().compareTo(new BigDecimal("10000.00")));
    }

    @Test void invalidUsefulLifeMeansNoDepreciation() {
        var d = calc.depreciation(asset("5000", "0", 0, LocalDate.now().minusYears(2)), LocalDate.now());
        assertEquals(0, d.current().compareTo(new BigDecimal("5000")));
    }

    @Test void warrantyStatusRules() {
        LocalDate t = LocalDate.now();
        assertEquals("NOT_AVAILABLE", calc.dateStatus(null, t).status());
        assertEquals("EXPIRED", calc.dateStatus(t.minusDays(1), t).status());
        assertEquals("EXPIRING_SOON", calc.dateStatus(t.plusDays(30), t).status());
        assertEquals("ACTIVE", calc.dateStatus(t.plusDays(31), t).status());
    }
}
