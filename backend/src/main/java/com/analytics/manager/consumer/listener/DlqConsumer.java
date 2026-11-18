package com.analytics.manager.consumer.listener;

import com.analytics.manager.common.repository.FailedEventRepository;
import com.analytics.manager.consumer.config.KafkaConsumerConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Component
public class DlqConsumer {

    private final FailedEventRepository failedEventRepository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public DlqConsumer(FailedEventRepository failedEventRepository,
                       ObjectMapper objectMapper,
                       MeterRegistry meterRegistry) {
        this.failedEventRepository = failedEventRepository;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    @KafkaListener(
            topics = KafkaConsumerConfig.DLQ_TOPIC,
            groupId = KafkaConsumerConfig.DLQ_CONSUMER_GROUP_ID,
            containerFactory = "dlqKafkaListenerContainerFactory"
    )
    public void consumeDlq(
            @Payload String rawPayload,
            @Header(value = KafkaHeaders.DLT_EXCEPTION_FQCN, required = false) String errorClass,
            @Header(value = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String errorMessage,
            @Header(value = KafkaHeaders.DLT_ORIGINAL_TOPIC, required = false) String originalTopic,
            @Header(value = KafkaHeaders.DLT_ORIGINAL_PARTITION, required = false) Integer originalPartition,
            @Header(value = KafkaHeaders.DLT_ORIGINAL_OFFSET, required = false) Long originalOffset,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int dlqPartition,
            @Header(KafkaHeaders.OFFSET) long dlqOffset,
            Acknowledgment acknowledgment
    ) {
        String topic = originalTopic != null ? originalTopic : KafkaConsumerConfig.MAIN_TOPIC;
        int partition = originalPartition != null ? originalPartition : dlqPartition;
        long offset = originalOffset != null ? originalOffset : dlqOffset;

        String eventId = null;
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            if (root.has("eventId") && !root.get("eventId").isNull()) {
                eventId = root.get("eventId").asText();
            }
        } catch (Exception e) {
            log.debug("DLQ payload could not be parsed as JSON: {}", e.getMessage());
        }

        log.warn("Persisting failed event to DLQ: eventId={} errorClass={} originalTopic={} partition={} offset={}",
                eventId, errorClass, topic, partition, offset);

        failedEventRepository.insertFailedEventIgnore(
                eventId,
                rawPayload,
                errorClass,
                errorMessage,
                topic,
                partition,
                offset,
                Instant.now()
        );

        Counter.builder("analytics_events_dlq_total")
                .description("Total events sent to dead-letter queue")
                .register(meterRegistry)
                .increment();

        if (acknowledgment != null) {
            acknowledgment.acknowledge();
        }
    }
}
