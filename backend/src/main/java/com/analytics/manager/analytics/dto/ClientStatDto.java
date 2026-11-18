package com.analytics.manager.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientStatDto {
    private String clientId;
    private long totalEvents;
    private Instant firstSeen;
    private Instant lastSeen;
}
