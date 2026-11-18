package com.analytics.manager.ingestion.service;

import com.analytics.manager.common.model.AnalyticsEvent;
import com.analytics.manager.ingestion.exception.KafkaPublishException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class KafkaProducerService {

    public static final String MAIN_TOPIC = "analytics-events";
    private static final long ACK_TIMEOUT_SECONDS = 3;

    private final KafkaTemplate<String, AnalyticsEvent> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    public KafkaProducerService(KafkaTemplate<String, AnalyticsEvent> kafkaTemplate, MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }

    public SendResult<String, AnalyticsEvent> sendEvent(AnalyticsEvent event) {
        try {
            log.debug("Sending eventId={} clientId={} to topic={}", event.getEventId(), event.getClientId(), MAIN_TOPIC);
            CompletableFuture<SendResult<String, AnalyticsEvent>> future = kafkaTemplate.send(
                    MAIN_TOPIC,
                    event.getClientId(),
                    event
            );

            // Synchronously wait for broker acknowledgment up to 3s
            SendResult<String, AnalyticsEvent> result = future.get(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            Counter.builder("analytics_events_published_total")
                    .tag("eventType", event.getEventType())
                    .description("Total events acknowledged by Kafka broker")
                    .register(meterRegistry)
                    .increment();

            log.info("Event published successfully: eventId={} partition={} offset={}",
                    event.getEventId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());

            return result;
        } catch (TimeoutException e) {
            recordFailure("timeout");
            log.error("Kafka ack timed out for eventId={}: {}", event.getEventId(), e.getMessage());
            throw new KafkaPublishException("Kafka broker did not acknowledge event within " + ACK_TIMEOUT_SECONDS + "s", "timeout", e);
        } catch (Exception e) {
            recordFailure("broker_error");
            log.error("Failed to publish eventId={} to Kafka: {}", event.getEventId(), e.getMessage());
            throw new KafkaPublishException("Failed to publish event to Kafka: " + e.getMessage(), "broker_error", e);
        }
    }

    private void recordFailure(String reason) {
        Counter.builder("analytics_events_publish_failed_total")
                .tag("reason", reason)
                .description("Total event publish failures")
                .register(meterRegistry)
                .increment();
    }
}
