package com.analytics.manager.consumer.service;

import com.analytics.manager.common.model.AnalyticsEvent;
import com.analytics.manager.common.repository.ClientStatsRepository;
import com.analytics.manager.common.repository.EventAggMinuteRepository;
import com.analytics.manager.common.repository.EventRepository;
import com.analytics.manager.common.repository.PageStatsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventProcessingServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventAggMinuteRepository eventAggMinuteRepository;

    @Mock
    private ClientStatsRepository clientStatsRepository;

    @Mock
    private PageStatsRepository pageStatsRepository;

    private MeterRegistry meterRegistry;
    private EventProcessingService processingService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        processingService = new EventProcessingService(
                eventRepository,
                eventAggMinuteRepository,
                clientStatsRepository,
                pageStatsRepository,
                new ObjectMapper(),
                meterRegistry
        );
    }

    @Test
    void testProcessNewEventUpdatesAggregatesAndPersists() {
        AnalyticsEvent event = AnalyticsEvent.builder()
                .eventId("test-uuid-1")
                .eventType("PAGE_VIEW")
                .clientId("client-1")
                .occurredAt(Instant.now())
                .receivedAt(Instant.now())
                .page("/dashboard")
                .metadata(Collections.singletonMap("theme", "dark"))
                .build();

        // Affected rows = 1 indicates successful fresh insert
        when(eventRepository.insertEventIgnore(any(), any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        boolean result = processingService.process(event, 0, 100L);

        assertTrue(result);
        verify(eventRepository).insertEventIgnore(any(), any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any(), any(), any());
        verify(eventAggMinuteRepository).upsertMinuteAgg(any(), eq("PAGE_VIEW"));
        verify(clientStatsRepository).upsertClientStats(eq("client-1"), any());
        verify(pageStatsRepository).upsertPageStats(eq("/dashboard"));

        double processedCount = meterRegistry.get("analytics_events_processed_total")
                .tag("eventType", "PAGE_VIEW")
                .counter()
                .count();
        assertEquals(1.0, processedCount);
    }

    @Test
    void testProcessDuplicateEventSkipsAggregatesPreservingAnalyticsIntegrity() {
        AnalyticsEvent duplicateEvent = AnalyticsEvent.builder()
                .eventId("existing-uuid")
                .eventType("LOGIN")
                .clientId("client-2")
                .occurredAt(Instant.now())
                .receivedAt(Instant.now())
                .build();

        // Affected rows = 0 indicates row already exists (duplicate)
        when(eventRepository.insertEventIgnore(any(), any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(0);

        boolean result = processingService.process(duplicateEvent, 1, 200L);

        assertFalse(result);
        verify(eventRepository).insertEventIgnore(any(), any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any(), any(), any());

        // CRITICAL: Aggregates must NOT be touched for duplicate events
        verify(eventAggMinuteRepository, never()).upsertMinuteAgg(any(), any());
        verify(clientStatsRepository, never()).upsertClientStats(any(), any());
        verify(pageStatsRepository, never()).upsertPageStats(any());

        double duplicatesCount = meterRegistry.get("analytics_events_duplicates_total")
                .counter()
                .count();
        assertEquals(1.0, duplicatesCount);
    }
}
