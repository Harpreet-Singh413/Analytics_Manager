package com.analytics.manager.consumer.service;

import com.analytics.manager.common.model.AnalyticsEvent;
import com.analytics.manager.common.repository.ClientStatsRepository;
import com.analytics.manager.common.repository.EventAggMinuteRepository;
import com.analytics.manager.common.repository.EventRepository;
import com.analytics.manager.common.repository.PageStatsRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class EventProcessingService {

    private final EventRepository eventRepository;
    private final EventAggMinuteRepository eventAggMinuteRepository;
    private final ClientStatsRepository clientStatsRepository;
    private final PageStatsRepository pageStatsRepository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public EventProcessingService(EventRepository eventRepository,
                                  EventAggMinuteRepository eventAggMinuteRepository,
                                  ClientStatsRepository clientStatsRepository,
                                  PageStatsRepository pageStatsRepository,
                                  ObjectMapper objectMapper,
                                  MeterRegistry meterRegistry) {
        this.eventRepository = eventRepository;
        this.eventAggMinuteRepository = eventAggMinuteRepository;
        this.clientStatsRepository = clientStatsRepository;
        this.pageStatsRepository = pageStatsRepository;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Atomically processes an event:
     * 1. Inserts event into events table with unique eventId constraint.
     * 2. If row was inserted (affected == 1), updates pre-aggregates.
     * 3. If duplicate (affected == 0), skips aggregate update.
     *
     * @return true if newly processed, false if duplicate skipped
     */
    @Transactional
    public boolean process(AnalyticsEvent event, int partition, long offset) {
        long startTime = System.nanoTime();
        try {
            String metadataJson = null;
            if (event.getMetadata() != null && !event.getMetadata().isEmpty()) {
                metadataJson = objectMapper.writeValueAsString(event.getMetadata());
            }

            Instant processedAt = Instant.now();

            // Atomic insert with duplicate prevention
            int affected = eventRepository.insertEventIgnore(
                    event.getEventId(),
                    event.getEventType(),
                    event.getClientId(),
                    event.getOccurredAt(),
                    event.getReceivedAt(),
                    processedAt,
                    event.getSource(),
                    event.getVersion() != null ? event.getVersion() : 1,
                    event.getPage(),
                    event.getProductId(),
                    metadataJson,
                    partition,
                    offset
            );

            if (affected == 0) {
                // Duplicate event detected: skip aggregates to preserve exact counts
                log.warn("Duplicate event skipped: eventId={} clientId={}", event.getEventId(), event.getClientId());
                Counter.builder("analytics_events_duplicates_total")
                        .description("Total duplicate events skipped")
                        .register(meterRegistry)
                        .increment();
                return false;
            }

            // Newly processed event -> update aggregates inside same transaction
            Instant bucketStart = event.getOccurredAt().truncatedTo(ChronoUnit.MINUTES);
            eventAggMinuteRepository.upsertMinuteAgg(bucketStart, event.getEventType());
            clientStatsRepository.upsertClientStats(event.getClientId(), event.getOccurredAt());

            if (event.getPage() != null && !event.getPage().trim().isEmpty()) {
                pageStatsRepository.upsertPageStats(event.getPage().trim());
            }

            // Business metrics
            Counter.builder("analytics_events_processed_total")
                    .tag("eventType", event.getEventType())
                    .description("Total events successfully processed and persisted")
                    .register(meterRegistry)
                    .increment();

            if (event.getReceivedAt() != null) {
                long e2eDurationNanos = Duration.between(event.getReceivedAt(), processedAt).toNanos();
                if (e2eDurationNanos >= 0) {
                    Timer.builder("analytics_event_end_to_end_seconds")
                            .description("End-to-end latency from API receivedAt to DB persistence")
                            .register(meterRegistry)
                            .record(e2eDurationNanos, TimeUnit.NANOSECONDS);
                }
            }

            log.info("Event successfully processed: eventId={} type={} partition={} offset={}",
                    event.getEventId(), event.getEventType(), partition, offset);
            return true;

        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize event metadata: " + e.getMessage(), e);
        } finally {
            long durationNanos = System.nanoTime() - startTime;
            Timer.builder("analytics_event_processing_seconds")
                    .description("Time taken by the consumer to process a single event record")
                    .register(meterRegistry)
                    .record(durationNanos, TimeUnit.NANOSECONDS);
        }
    }
}
