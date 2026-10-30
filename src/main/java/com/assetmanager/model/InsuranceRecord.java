package com.assetmanager.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;

@Entity
@Table(name = "insurance_records")
@Getter @Setter @NoArgsConstructor
public class InsuranceRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(optional = false) @JoinColumn(name = "asset_id", nullable = false, unique = true) private Asset asset;
    @Column(length = 150) private String provider;
    @Column(length = 100) private String policyNumber;
    @Column(nullable = false) private LocalDate startDate;
    @Column(nullable = false) private LocalDate endDate;
}
