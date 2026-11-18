package com.analytics.manager.ingestion.controller;

import com.analytics.manager.common.model.AnalyticsEvent;
import com.analytics.manager.common.model.EventType;
import com.analytics.manager.ingestion.dto.BatchEventRequestDto;
import com.analytics.manager.ingestion.dto.BatchEventResponseDto;
import com.analytics.manager.ingestion.dto.EventRequestDto;
import com.analytics.manager.ingestion.dto.EventResponseDto;
import com.analytics.manager.ingestion.service.EventEnrichmentService;
import com.analytics.manager.ingestion.service.KafkaProducerService;
import com.analytics.manager.ingestion.validation.EventTypeValidationException;
import com.analytics.manager.ingestion.validation.EventValidationService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/events")
public class EventIngestionController {

    private final EventValidationService validationService;
    private final EventEnrichmentService enrichmentService;
    private final KafkaProducerService producerService;

    public EventIngestionController(EventValidationService validationService,
                                    EventEnrichmentService enrichmentService,
                                    KafkaProducerService producerService) {
        this.validationService = validationService;
        this.enrichmentService = enrichmentService;
        this.producerService = producerService;
    }

    @PostMapping
    public ResponseEntity<EventResponseDto> ingestEvent(@Valid @RequestBody EventRequestDto request) {
        EventType eventType = validationService.validate(request);
        AnalyticsEvent event = enrichmentService.enrich(request, eventType);

        producerService.sendEvent(event);

        EventResponseDto response = EventResponseDto.builder()
                .eventId(event.getEventId())
                .status("ACCEPTED")
                .receivedAt(event.getReceivedAt())
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/events/" + event.getEventId()))
                .body(response);
    }

    @PostMapping("/batch")
    public ResponseEntity<BatchEventResponseDto> ingestBatch(@Valid @RequestBody BatchEventRequestDto batchRequest) {
        int accepted = 0;
        List<BatchEventResponseDto.BatchItemError> rejected = new ArrayList<>();

        List<EventRequestDto> events = batchRequest.getEvents();
        for (int i = 0; i < events.size(); i++) {
            EventRequestDto dto = events.get(i);
            try {
                EventType eventType = validationService.validate(dto);
                AnalyticsEvent event = enrichmentService.enrich(dto, eventType);
                producerService.sendEvent(event);
                accepted++;
            } catch (EventTypeValidationException e) {
                rejected.add(new BatchEventResponseDto.BatchItemError(i, e.getErrors()));
            } catch (Exception e) {
                rejected.add(new BatchEventResponseDto.BatchItemError(i, Collections.singletonList(e.getMessage())));
            }
        }

        BatchEventResponseDto response = BatchEventResponseDto.builder()
                .accepted(accepted)
                .rejected(rejected)
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
