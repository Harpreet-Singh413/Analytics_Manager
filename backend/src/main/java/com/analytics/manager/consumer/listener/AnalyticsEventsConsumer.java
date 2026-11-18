package com.analytics.manager.consumer.listener;

import com.analytics.manager.common.model.AnalyticsEvent;
import com.analytics.manager.consumer.config.KafkaConsumerConfig;
import com.analytics.manager.consumer.service.EventProcessingService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AnalyticsEventsConsumer {

    private final EventProcessingService processingService;
    private final MeterRegistry meterRegistry;

    public AnalyticsEventsConsumer(EventProcessingService processingService, MeterRegistry meterRegistry) {
        this.processingService = processingService;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(
            topics = "analytics-events",
            groupId = KafkaConsumerConfig.CONSUMER_GROUP_ID,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            @Payload AnalyticsEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment
    ) {
        try {
            if (event.getEventId() != null) {
                MDC.put("eventId", event.getEventId());
            }
            if (event.getClientId() != null) {
                MDC.put("clientId", event.getClientId());
            }
            MDC.put("partition", String.valueOf(partition));
            MDC.put("offset", String.valueOf(offset));

            log.debug("Received event from Kafka: eventId={} type={} partition={} offset={}",
                    event.getEventId(), event.getEventType(), partition, offset);

            Counter.builder("analytics_events_consumed_total")
                    .description("Total records received by the listener")
                    .register(meterRegistry)
                    .increment();

            // Transactional processing (DB write + pre-aggregates + duplicate check)
            processingService.process(event, partition, offset);

            // Commit offset after database transaction has successfully committed
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }

        } finally {
            MDC.clear();
        }
    }
}
