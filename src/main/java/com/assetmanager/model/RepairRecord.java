package com.assetmanager.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "repair_records")
@Getter @Setter @NoArgsConstructor
public class RepairRecord implements CostRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "asset_id", nullable = false) private Asset asset;
    @NotNull(message = "Repair date is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Column(nullable = false) private LocalDate repairDate;
    @NotBlank(message = "Problem description is required")
    @Column(nullable = false, length = 1000) private String problemDescription;
    @Column(length = 50) private String repairType;
    @Column(length = 150) private String provider;
    @NotNull(message = "Cost is required") @DecimalMin(value = "0.00", message = "Cost cannot be negative")
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal cost;
    @Column(length = 500) private String partsReplaced;
    @Column(nullable = false, length = 20) private String status = "PENDING";   // PENDING, IN_PROGRESS, COMPLETED
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate completionDate;
    @Column(length = 1000) private String notes;

    @Override public LocalDate getRecordDate() { return repairDate; }
    @Override public String getRecordType() { return "REPAIR"; }
}
