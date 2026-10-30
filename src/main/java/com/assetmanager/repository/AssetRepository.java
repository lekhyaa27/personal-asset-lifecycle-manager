package com.assetmanager.repository;

import com.assetmanager.model.Asset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    // Every query includes the user id, so one user can never reach another user's assets.
    Optional<Asset> findByIdAndUserId(Long id, Long userId);
    List<Asset> findByUserIdOrderByNameAsc(Long userId);
    List<Asset> findByUserIdAndStatusNotOrderByNameAsc(Long userId, String status);

    Optional<Asset> findByPublicToken(String publicToken);

    @Query("select a from Asset a where a.user.id = :uid " +
           "and (:q = '' or lower(a.name) like lower(concat('%', :q, '%')) or lower(coalesce(a.brand,'')) like lower(concat('%', :q, '%'))) " +
           "and (:cat = '' or a.category = :cat) and (:status = '' or a.status = :status)")
    Page<Asset> search(@Param("uid") Long uid, @Param("q") String q, @Param("cat") String cat,
                       @Param("status") String status, Pageable pageable);
}
