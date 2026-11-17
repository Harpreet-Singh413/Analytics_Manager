package com.analytics.manager.common.repository;

import com.analytics.manager.common.entity.EventAggMinuteEntity;
import com.analytics.manager.common.entity.EventAggMinuteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface EventAggMinuteRepository extends JpaRepository<EventAggMinuteEntity, EventAggMinuteId> {

    @Modifying
    @Query(value = """
        INSERT INTO event_agg_minute (bucket_start, event_type, event_count)
        VALUES (:bucketStart, :eventType, 1)
        ON DUPLICATE KEY UPDATE event_count = event_count + 1
        """, nativeQuery = true)
    int upsertMinuteAgg(@Param("bucketStart") Instant bucketStart, @Param("eventType") String eventType);

    @Query("SELECT COALESCE(SUM(e.eventCount), 0) FROM EventAggMinuteEntity e")
    long getTotalEventsCount();

    @Query("SELECT COALESCE(SUM(e.eventCount), 0) FROM EventAggMinuteEntity e WHERE e.id.bucketStart >= :since")
    long getEventsCountSince(@Param("since") Instant since);

    interface EventTypeCount {
        String getEventType();
        Long getCount();
    }

    @Query("SELECT e.id.eventType as eventType, SUM(e.eventCount) as count " +
           "FROM EventAggMinuteEntity e " +
           "WHERE e.id.bucketStart >= :from AND e.id.bucketStart <= :to " +
           "GROUP BY e.id.eventType")
    List<EventTypeCount> getCountsByEventType(@Param("from") Instant from, @Param("to") Instant to);

    interface BucketCount {
        Instant getBucketStart();
        Long getCount();
    }

    @Query("SELECT e.id.bucketStart as bucketStart, SUM(e.eventCount) as count " +
           "FROM EventAggMinuteEntity e " +
           "WHERE e.id.bucketStart >= :from AND e.id.bucketStart <= :to " +
           "GROUP BY e.id.bucketStart " +
           "ORDER BY e.id.bucketStart ASC")
    List<BucketCount> getEventsOverTime(@Param("from") Instant from, @Param("to") Instant to);
}
