package com.analytics.manager.ingestion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
public class EventRequestDto {

    private String eventId;

    @NotBlank(message = "eventType is required")
    private String eventType;

    @NotBlank(message = "clientId is required")
    @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$", message = "clientId must be 1-64 characters matching [A-Za-z0-9_-]+")
    private String clientId;

    private Instant occurredAt;

    @Size(max = 255, message = "page must be at most 255 characters")
    private String page;

    @Size(max = 64, message = "productId must be at most 64 characters")
    private String productId;

    private Map<String, Object> metadata;

    @Size(max = 50, message = "source must be at most 50 characters")
    private String source;
}
