package com.analytics.manager.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "events", indexes = {
    @Index(name = "uk_events_event_id", columnList = "event_id", unique = true),
    @Index(name = "idx_events_occurred_at", columnList = "occurred_at DESC"),
    @Index(name = "idx_events_type_occurred", columnList = "event_type, occurred_at DESC"),
    @Index(name = "idx_events_client_occurred", columnList = "client_id, occurred_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Column(name = "source", nullable = false, length = 50)
    private String source;

    @Column(name = "schema_version", nullable = false)
    private Integer schemaVersion;

    @Column(name = "page", length = 255)
    private String page;

    @Column(name = "product_id", length = 64)
    private String productId;

    @Column(name = "metadata", columnDefinition = "json")
    private String metadata;

    @Column(name = "kafka_partition")
    private Integer kafkaPartition;

    @Column(name = "kafka_offset")
    private Long kafkaOffset;
}
