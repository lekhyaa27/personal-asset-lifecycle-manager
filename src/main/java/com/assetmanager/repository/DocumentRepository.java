package com.assetmanager.repository;

import com.assetmanager.model.AssetDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<AssetDocument, Long> {
    List<AssetDocument> findByAssetIdOrderByUploadedAtDesc(Long assetId);
    Optional<AssetDocument> findByIdAndAssetUserId(Long id, Long userId);
}
