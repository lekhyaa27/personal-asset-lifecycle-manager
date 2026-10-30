package com.assetmanager.repository;

import com.assetmanager.model.MaintenanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface MaintenanceRepository extends JpaRepository<MaintenanceRecord, Long> {
    List<MaintenanceRecord> findByAssetUserIdOrderByMaintenanceDateDesc(Long userId);
    List<MaintenanceRecord> findByAssetIdOrderByMaintenanceDateDesc(Long assetId);

    @Query("select sum(m.cost) from MaintenanceRecord m where m.asset.user.id = :uid")
    BigDecimal totalByUser(@Param("uid") Long uid);

    @Query("select sum(m.cost) from MaintenanceRecord m where m.asset.id = :aid")
    BigDecimal totalByAsset(@Param("aid") Long aid);

    // GROUP BY example used by the dashboard chart
    @Query("select m.asset.name, sum(m.cost) from MaintenanceRecord m where m.asset.user.id = :uid group by m.asset.name order by sum(m.cost) desc")
    List<Object[]> costByAsset(@Param("uid") Long uid);
}
