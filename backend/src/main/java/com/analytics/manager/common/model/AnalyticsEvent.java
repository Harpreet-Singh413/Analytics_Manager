package com.analytics.manager.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsEvent implements Serializable {
    private String eventId;
    private String eventType;
    private String clientId;
    private Instant occurredAt;
    private Instant receivedAt;
    private String source;
    private Integer version;
    private String page;
    private String productId;
    private Map<String, Object> metadata;
}
