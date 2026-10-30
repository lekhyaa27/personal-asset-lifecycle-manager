package com.assetmanager.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

/** Only metadata is stored in MySQL; the file itself lives in the uploads/ folder. */
@Entity
@Table(name = "asset_documents")
@Getter @Setter @NoArgsConstructor
public class AssetDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "asset_id", nullable = false) private Asset asset;
    @Column(nullable = false, length = 50) private String docType;
    @Column(nullable = false) private String originalName;
    @Column(nullable = false) private String storedName;
    private String contentType;
    private Long sizeBytes;
    private LocalDateTime uploadedAt = LocalDateTime.now();
}
