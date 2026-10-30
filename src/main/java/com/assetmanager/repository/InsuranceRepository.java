package com.assetmanager.repository;

import com.assetmanager.model.InsuranceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface InsuranceRepository extends JpaRepository<InsuranceRecord, Long> {
    Optional<InsuranceRecord> findByAssetId(Long assetId);
    List<InsuranceRecord> findByAssetUserId(Long userId);
}
