package com.assetmanager.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One row of the "assets" table. Many assets belong to one user. */
@Entity
@Table(name = "assets")
@Getter @Setter @NoArgsConstructor
public class Asset {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false, length = 150) private String name;
    @Column(nullable = false, length = 50) private String category;
    @Column(length = 100) private String brand;
    @Column(length = 100) private String model;
    @Column(length = 100) private String serialNumber;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal purchasePrice;
    @Column(nullable = false) private LocalDate purchaseDate;
    @Column(length = 150) private String purchaseLocation;
    @Column(length = 20) private String assetCondition;   // EXCELLENT, GOOD, FAIR, POOR
    private Integer usefulLifeYears;
    @Column(precision = 12, scale = 2) private BigDecimal salvageValue;
    @Column(length = 1000) private String description;
    @Column(length = 1000) private String notes;
    @Column(nullable = false, length = 20) private String status = "ACTIVE";
    private LocalDate disposedDate;
    /** Random, hard-to-guess code used in the QR link so the public page does not expose the numeric id. */
    @Column(unique = true, length = 40) private String publicToken;
    private LocalDateTime createdAt = LocalDateTime.now();
}
