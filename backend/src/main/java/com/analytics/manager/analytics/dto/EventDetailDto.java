package com.analytics.manager.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventDetailDto {
    private String eventId;
    private String eventType;
    private String clientId;
    private Instant occurredAt;
    private Instant receivedAt;
    private Instant processedAt;
    private String source;
    private Integer schemaVersion;
    private String page;
    private String productId;
    private Map<String, Object> metadata;
    private Integer kafkaPartition;
    private Long kafkaOffset;
    private String status; // "PROCESSED" or "FAILED"
    private String errorClass;
    private String errorMessage;
    private Instant failedAt;
}
