package com.assetmanager.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Interface (abstraction + polymorphism): maintenance, repair and upgrade records all have a cost and a date. */
public interface CostRecord {
    BigDecimal getCost();
    LocalDate getRecordDate();
    String getRecordType();
}
