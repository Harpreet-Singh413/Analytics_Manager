package com.analytics.manager.common.health;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.common.Node;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Custom Kafka health indicator that verifies broker connectivity and cluster metadata
 * using AdminClient.describeCluster with a bounded timeout (3s).
 * Included in the Actuator 'readiness' group.
 */
@Slf4j
@Component("kafkaHealthIndicator")
public class KafkaHealthIndicator implements HealthIndicator {

    private final KafkaAdmin kafkaAdmin;
    private final Function<Map<String, Object>, AdminClient> adminClientFactory;
    private static final int TIMEOUT_SECONDS = 3;

    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        this(kafkaAdmin, AdminClient::create);
    }

    KafkaHealthIndicator(KafkaAdmin kafkaAdmin, Function<Map<String, Object>, AdminClient> adminClientFactory) {
        this.kafkaAdmin = kafkaAdmin;
        this.adminClientFactory = adminClientFactory;
    }

    @Override
    public Health health() {
        try (AdminClient adminClient = adminClientFactory.apply(kafkaAdmin.getConfigurationProperties())) {
            DescribeClusterResult cluster = adminClient.describeCluster(
                    new DescribeClusterOptions().timeoutMs(TIMEOUT_SECONDS * 1000)
            );

            String clusterId = cluster.clusterId().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Node controller = cluster.controller().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Collection<Node> nodes = cluster.nodes().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            return Health.up()
                    .withDetail("clusterId", clusterId)
                    .withDetail("nodesCount", nodes.size())
                    .withDetail("controller", controller != null ? controller.host() + ":" + controller.port() : "unknown")
                    .build();
        } catch (Exception e) {
            log.warn("Kafka readiness check failed: {}", e.getMessage());
            return Health.down(e)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
