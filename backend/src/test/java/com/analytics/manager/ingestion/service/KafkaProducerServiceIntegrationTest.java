package com.analytics.manager.ingestion.service;

import com.analytics.manager.common.model.AnalyticsEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EmbeddedKafka(partitions = 3, topics = {KafkaProducerService.MAIN_TOPIC})
class KafkaProducerServiceIntegrationTest {

    private KafkaProducerService producerService;
    private MeterRegistry meterRegistry;
    private Consumer<String, AnalyticsEvent> consumer;

    @BeforeEach
    void setUp(EmbeddedKafkaBroker embeddedKafka) {
        meterRegistry = new SimpleMeterRegistry();

        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafka);
        producerProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        DefaultKafkaProducerFactory<String, AnalyticsEvent> producerFactory =
                new DefaultKafkaProducerFactory<>(producerProps, new org.apache.kafka.common.serialization.StringSerializer(), new JsonSerializer<>());

        KafkaTemplate<String, AnalyticsEvent> kafkaTemplate = new KafkaTemplate<>(producerFactory);
        producerService = new KafkaProducerService(kafkaTemplate, meterRegistry);

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("test-group", "true", embeddedKafka);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        JsonDeserializer<AnalyticsEvent> jsonDeserializer = new JsonDeserializer<>(AnalyticsEvent.class);
        jsonDeserializer.addTrustedPackages("*");

        DefaultKafkaConsumerFactory<String, AnalyticsEvent> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), jsonDeserializer);
        consumer = consumerFactory.createConsumer();
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, KafkaProducerService.MAIN_TOPIC);
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    void testSendEventSuccessfullyPublishesToKafka() {
        String eventId = UUID.randomUUID().toString();
        String clientId = "client-integ-1";

        AnalyticsEvent event = AnalyticsEvent.builder()
                .eventId(eventId)
                .eventType("PAGE_VIEW")
                .clientId(clientId)
                .occurredAt(Instant.now())
                .receivedAt(Instant.now())
                .source("integration-test")
                .version(1)
                .page("/test-page")
                .metadata(Collections.singletonMap("browser", "Chrome"))
                .build();

        SendResult<String, AnalyticsEvent> result = producerService.sendEvent(event);

        assertNotNull(result);
        assertEquals(KafkaProducerService.MAIN_TOPIC, result.getRecordMetadata().topic());

        ConsumerRecords<String, AnalyticsEvent> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));
        assertFalse(records.isEmpty());

        ConsumerRecord<String, AnalyticsEvent> record = records.iterator().next();
        assertEquals(clientId, record.key());
        assertEquals(eventId, record.value().getEventId());
        assertEquals("PAGE_VIEW", record.value().getEventType());
        assertEquals("/test-page", record.value().getPage());

        double publishedCount = meterRegistry.get("analytics_events_published_total")
                .tag("eventType", "PAGE_VIEW")
                .counter()
                .count();
        assertEquals(1.0, publishedCount);
    }
}
