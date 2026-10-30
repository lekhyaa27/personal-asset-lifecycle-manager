package com.assetmanager.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;

/** One warranty per asset (one-to-one). Status is calculated from dates, never stored. */
@Entity
@Table(name = "warranty_records")
@Getter @Setter @NoArgsConstructor
public class WarrantyRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(optional = false) @JoinColumn(name = "asset_id", nullable = false, unique = true) private Asset asset;
    @Column(length = 150) private String provider;
    @Column(nullable = false) private LocalDate startDate;
    @Column(nullable = false) private LocalDate endDate;
}
