package com.assetmanager.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

/** Timeline of everything that happened to an asset. */
@Entity
@Table(name = "lifecycle_history")
@Getter @Setter @NoArgsConstructor
public class LifecycleHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "asset_id", nullable = false) private Asset asset;
    @Column(nullable = false, length = 30) private String eventType;
    @Column(nullable = false, length = 500) private String description;
    @Column(length = 20) private String oldStatus;
    @Column(length = 20) private String newStatus;
    @Column(nullable = false) private LocalDateTime eventTime = LocalDateTime.now();
}
