package com.assetmanager.repository;

import com.assetmanager.model.LifecycleHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HistoryRepository extends JpaRepository<LifecycleHistory, Long> {
    List<LifecycleHistory> findByAssetIdOrderByEventTimeDesc(Long assetId);
    List<LifecycleHistory> findTop10ByAssetUserIdOrderByEventTimeDesc(Long userId);
    List<LifecycleHistory> findByAssetUserIdOrderByEventTimeDesc(Long userId);
}
