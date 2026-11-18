package com.analytics.manager.consumer.listener;

import com.analytics.manager.common.repository.FailedEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DlqConsumerTest {

    @Mock
    private FailedEventRepository failedEventRepository;

    @Mock
    private Acknowledgment acknowledgment;

    private MeterRegistry meterRegistry;
    private DlqConsumer dlqConsumer;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        dlqConsumer = new DlqConsumer(failedEventRepository, new ObjectMapper(), meterRegistry);
    }

    @Test
    void testConsumeDlqWithParseableJsonSavesEventIdAndIncrementsMetric() {
        String payload = """
                {"eventId":"e8470a2c-1b77-4c07-b28e-8a2db7a31b20","eventType":"PURCHASE","clientId":"client-1"}
                """;

        dlqConsumer.consumeDlq(
                payload,
                "java.lang.IllegalArgumentException",
                "Validation failed: missing productId",
                "analytics-events",
                1,
                150L,
                0,
                5L,
                acknowledgment
        );

        verify(failedEventRepository).insertFailedEventIgnore(
                eq("e8470a2c-1b77-4c07-b28e-8a2db7a31b20"),
                eq(payload),
                eq("java.lang.IllegalArgumentException"),
                eq("Validation failed: missing productId"),
                eq("analytics-events"),
                eq(1),
                eq(150L),
                any()
        );

        verify(acknowledgment).acknowledge();

        double dlqCount = meterRegistry.get("analytics_events_dlq_total").counter().count();
        assertEquals(1.0, dlqCount);
    }

    @Test
    void testConsumeDlqWithPoisonPillSavesWithNullEventIdAndAcknowledges() {
        String poisonPill = "MALFORMED_UNPARSEABLE_STRING_NON_JSON";

        dlqConsumer.consumeDlq(
                poisonPill,
                "org.apache.kafka.common.errors.SerializationException",
                "Cannot deserialize bytes",
                "analytics-events",
                0,
                99L,
                0,
                6L,
                acknowledgment
        );

        verify(failedEventRepository).insertFailedEventIgnore(
                isNull(),
                eq(poisonPill),
                eq("org.apache.kafka.common.errors.SerializationException"),
                eq("Cannot deserialize bytes"),
                eq("analytics-events"),
                eq(0),
                eq(99L),
                any()
        );

        verify(acknowledgment).acknowledge();

        double dlqCount = meterRegistry.get("analytics_events_dlq_total").counter().count();
        assertEquals(1.0, dlqCount);
    }
}
