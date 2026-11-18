package com.analytics.manager.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsSummaryDto {
    private long totalEvents;
    private long eventsLastMinute;
    private long eventsLast5Minutes;
    private long activeClients;
    private long failedEvents;
    private Double avgProcessingLatencyMs;
}
