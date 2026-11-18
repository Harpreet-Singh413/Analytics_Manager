package com.analytics.manager.ingestion.service;

import com.analytics.manager.common.model.AnalyticsEvent;
import com.analytics.manager.common.model.EventType;
import com.analytics.manager.ingestion.dto.EventRequestDto;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class EventEnrichmentService {

    public AnalyticsEvent enrich(EventRequestDto dto, EventType eventType) {
        Instant now = Instant.now();
        String eventId = (dto.getEventId() != null && !dto.getEventId().trim().isEmpty())
                ? dto.getEventId().trim().toLowerCase()
                : UUID.randomUUID().toString();

        Instant occurredAt = dto.getOccurredAt() != null ? dto.getOccurredAt() : now;
        String source = (dto.getSource() != null && !dto.getSource().trim().isEmpty())
                ? dto.getSource().trim()
                : "react-simulator";

        return AnalyticsEvent.builder()
                .eventId(eventId)
                .eventType(eventType.name())
                .clientId(dto.getClientId().trim())
                .occurredAt(occurredAt)
                .receivedAt(now)
                .source(source)
                .version(1)
                .page(dto.getPage() != null ? dto.getPage().trim() : null)
                .productId(dto.getProductId() != null ? dto.getProductId().trim() : null)
                .metadata(dto.getMetadata())
                .build();
    }
}
