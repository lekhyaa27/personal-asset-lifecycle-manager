package com.assetmanager.repository;

import com.assetmanager.model.RepairRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface RepairRepository extends JpaRepository<RepairRecord, Long> {
    List<RepairRecord> findByAssetUserIdOrderByRepairDateDesc(Long userId);
    List<RepairRecord> findByAssetIdOrderByRepairDateDesc(Long assetId);
    Optional<RepairRecord> findByIdAndAssetUserId(Long id, Long userId);
    long countByAssetIdAndStatusNot(Long assetId, String status);

    @Query("select sum(r.cost) from RepairRecord r where r.asset.user.id = :uid")
    BigDecimal totalByUser(@Param("uid") Long uid);

    @Query("select sum(r.cost) from RepairRecord r where r.asset.id = :aid")
    BigDecimal totalByAsset(@Param("aid") Long aid);
}
