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
public class FailedEventDto {
    private Long id;
    private String eventId;
    private String payload;
    private String errorClass;
    private String errorMessage;
    private String originalTopic;
    private Integer originalPartition;
    private Long originalOffset;
    private Instant failedAt;
}
