package com.analytics.manager.common.repository;

import com.analytics.manager.common.entity.FailedEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface FailedEventRepository extends JpaRepository<FailedEventEntity, Long> {

    Optional<FailedEventEntity> findByEventId(String eventId);

    Page<FailedEventEntity> findAllByOrderByFailedAtDesc(Pageable pageable);

    @Modifying
    @Query(value = """
        INSERT IGNORE INTO failed_events 
        (event_id, payload, error_class, error_message, original_topic, original_partition, original_offset, failed_at)
        VALUES 
        (:eventId, :payload, :errorClass, :errorMessage, :originalTopic, :originalPartition, :originalOffset, :failedAt)
        """, nativeQuery = true)
    int insertFailedEventIgnore(
        @Param("eventId") String eventId,
        @Param("payload") String payload,
        @Param("errorClass") String errorClass,
        @Param("errorMessage") String errorMessage,
        @Param("originalTopic") String originalTopic,
        @Param("originalPartition") int originalPartition,
        @Param("originalOffset") long originalOffset,
        @Param("failedAt") Instant failedAt
    );
}

