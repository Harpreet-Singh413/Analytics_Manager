package com.analytics.manager.common.repository;

import com.analytics.manager.common.entity.ClientStatsEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ClientStatsRepository extends JpaRepository<ClientStatsEntity, String> {

    @Modifying
    @Query(value = """
        INSERT INTO client_stats (client_id, total_events, first_seen, last_seen)
        VALUES (:clientId, 1, :occurredAt, :occurredAt)
        ON DUPLICATE KEY UPDATE 
            total_events = total_events + 1, 
            last_seen = GREATEST(last_seen, :occurredAt),
            first_seen = LEAST(first_seen, :occurredAt)
        """, nativeQuery = true)
    int upsertClientStats(@Param("clientId") String clientId, @Param("occurredAt") Instant occurredAt);

    List<ClientStatsEntity> findAllByOrderByTotalEventsDesc(Pageable pageable);

    List<ClientStatsEntity> findAllByOrderByLastSeenDesc(Pageable pageable);
}
