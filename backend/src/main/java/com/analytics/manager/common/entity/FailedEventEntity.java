package com.analytics.manager.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "failed_events", uniqueConstraints = {
    @UniqueConstraint(name = "uk_failed_events_record", columnNames = {"original_topic", "original_partition", "original_offset"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FailedEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "error_class")
    private String errorClass;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "original_topic", nullable = false, length = 100)
    private String originalTopic;

    @Column(name = "original_partition", nullable = false)
    private Integer originalPartition;

    @Column(name = "original_offset", nullable = false)
    private Long originalOffset;

    @Column(name = "failed_at", nullable = false)
    private Instant failedAt;
}
