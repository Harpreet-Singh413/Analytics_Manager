package com.analytics.manager.consumer.listener;

import com.analytics.manager.common.model.AnalyticsEvent;
import com.analytics.manager.consumer.service.EventProcessingService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AnalyticsEventsConsumerTest {

    @Mock
    private EventProcessingService processingService;

    @Mock
    private Acknowledgment acknowledgment;

    private MeterRegistry meterRegistry;
    private AnalyticsEventsConsumer consumer;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        consumer = new AnalyticsEventsConsumer(processingService, meterRegistry);
    }

    @Test
    void testConsumeDelegatesToServiceAndAcknowledgesOffset() {
        AnalyticsEvent event = AnalyticsEvent.builder()
                .eventId("event-123")
                .eventType("PAGE_VIEW")
                .clientId("client-abc")
                .occurredAt(Instant.now())
                .receivedAt(Instant.now())
                .build();

        consumer.consume(event, 2, 45L, acknowledgment);

        // Verify delegated to service
        verify(processingService).process(event, 2, 45L);

        // Verify offset manually acknowledged
        verify(acknowledgment).acknowledge();

        // Verify metric incremented
        double consumedCount = meterRegistry.get("analytics_events_consumed_total").counter().count();
        assertEquals(1.0, consumedCount);

        // Verify MDC cleaned up in finally block
        assertNull(MDC.get("eventId"));
        assertNull(MDC.get("clientId"));
        assertNull(MDC.get("partition"));
        assertNull(MDC.get("offset"));
    }
}
