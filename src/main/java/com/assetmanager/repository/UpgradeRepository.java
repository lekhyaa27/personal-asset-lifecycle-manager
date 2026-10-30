package com.assetmanager.repository;

import com.assetmanager.model.UpgradeRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface UpgradeRepository extends JpaRepository<UpgradeRecord, Long> {
    List<UpgradeRecord> findByAssetUserIdOrderByUpgradeDateDesc(Long userId);
    List<UpgradeRecord> findByAssetIdOrderByUpgradeDateDesc(Long assetId);
    Optional<UpgradeRecord> findByIdAndAssetUserId(Long id, Long userId);

    @Query("select sum(u.cost) from UpgradeRecord u where u.asset.user.id = :uid")
    BigDecimal totalByUser(@Param("uid") Long uid);

    @Query("select sum(u.cost) from UpgradeRecord u where u.asset.id = :aid")
    BigDecimal totalByAsset(@Param("aid") Long aid);
}
