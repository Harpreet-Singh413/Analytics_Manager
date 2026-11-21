package com.analytics.manager.ingestion.config;

import com.analytics.manager.common.model.AnalyticsEvent;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ProducerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class KafkaProducerConfigTest {

    @Test
    void testProducerFactoryCanCreateProducer() {
        KafkaProducerConfig config = new KafkaProducerConfig();
        // inject bootstrapServers using reflection or field
        try {
            java.lang.reflect.Field field = KafkaProducerConfig.class.getDeclaredField("bootstrapServers");
            field.setAccessible(true);
            field.set(config, "localhost:9092");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ProducerFactory<String, AnalyticsEvent> factory = config.producerFactory();
        assertNotNull(factory.createProducer());
    }
}
