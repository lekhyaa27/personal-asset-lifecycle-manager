package com.assetmanager.repository;

import com.assetmanager.model.WarrantyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WarrantyRepository extends JpaRepository<WarrantyRecord, Long> {
    Optional<WarrantyRecord> findByAssetId(Long assetId);
    List<WarrantyRecord> findByAssetUserId(Long userId);
}
