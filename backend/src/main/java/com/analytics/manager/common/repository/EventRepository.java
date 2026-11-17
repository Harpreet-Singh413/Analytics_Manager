package com.analytics.manager.common.repository;

import com.analytics.manager.common.entity.EventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<EventEntity, Long>, JpaSpecificationExecutor<EventEntity> {

    Optional<EventEntity> findByEventId(String eventId);

    /**
     * Atomically inserts event with deduplication.
     * Returns 1 if inserted, 0 if duplicate (skipped).
     */
    @Modifying
    @Query(value = """
        INSERT IGNORE INTO events 
        (event_id, event_type, client_id, occurred_at, received_at, processed_at, source, schema_version, page, product_id, metadata, kafka_partition, kafka_offset)
        VALUES 
        (:eventId, :eventType, :clientId, :occurredAt, :receivedAt, :processedAt, :source, :schemaVersion, :page, :productId, :metadata, :kafkaPartition, :kafkaOffset)
        """, nativeQuery = true)
    int insertEventIgnore(
        @Param("eventId") String eventId,
        @Param("eventType") String eventType,
        @Param("clientId") String clientId,
        @Param("occurredAt") Instant occurredAt,
        @Param("receivedAt") Instant receivedAt,
        @Param("processedAt") Instant processedAt,
        @Param("source") String source,
        @Param("schemaVersion") int schemaVersion,
        @Param("page") String page,
        @Param("productId") String productId,
        @Param("metadata") String metadata,
        @Param("kafkaPartition") Integer kafkaPartition,
        @Param("kafkaOffset") Long kafkaOffset
    );

    @Query("SELECT COUNT(DISTINCT e.clientId) FROM EventEntity e WHERE e.receivedAt > :since")
    long countActiveClientsSince(@Param("since") Instant since);

    List<EventEntity> findTop10ByOrderByOccurredAtDesc();

    @Query(value = "SELECT AVG(TIMESTAMPDIFF(MICROSECOND, received_at, processed_at) / 1000.0) FROM events WHERE received_at > :since", nativeQuery = true)
    Double findAvgProcessingLatencyMsSince(@Param("since") Instant since);
}
