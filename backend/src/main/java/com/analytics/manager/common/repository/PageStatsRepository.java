package com.analytics.manager.common.repository;

import com.analytics.manager.common.entity.PageStatsEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PageStatsRepository extends JpaRepository<PageStatsEntity, String> {

    @Modifying
    @Query(value = """
        INSERT INTO page_stats (page, view_count)
        VALUES (:page, 1)
        ON DUPLICATE KEY UPDATE view_count = view_count + 1
        """, nativeQuery = true)
    int upsertPageStats(@Param("page") String page);

    List<PageStatsEntity> findAllByOrderByViewCountDesc(Pageable pageable);
}
