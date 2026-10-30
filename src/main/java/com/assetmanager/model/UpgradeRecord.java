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
@Table(name = "upgrade_records")
@Getter @Setter @NoArgsConstructor
public class UpgradeRecord implements CostRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "asset_id", nullable = false) private Asset asset;
    @NotNull(message = "Upgrade date is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Column(nullable = false) private LocalDate upgradeDate;
    @NotBlank(message = "Upgrade description is required")
    @Column(nullable = false, length = 1000) private String description;
    @NotNull(message = "Cost is required") @DecimalMin(value = "0.00", message = "Cost cannot be negative")
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal cost;
    @Column(length = 150) private String provider;
    @Column(length = 1000) private String notes;

    @Override public LocalDate getRecordDate() { return upgradeDate; }
    @Override public String getRecordType() { return "UPGRADE"; }
}
