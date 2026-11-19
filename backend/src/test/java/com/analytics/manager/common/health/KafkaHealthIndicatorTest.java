package com.analytics.manager.common.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaHealthIndicatorTest {

    @Mock
    private KafkaAdmin kafkaAdmin;

    @Mock
    private AdminClient adminClient;

    @Mock
    private DescribeClusterResult describeClusterResult;

    @Test
    @DisplayName("health() returns UP with cluster details when Kafka is reachable")
    void shouldReturnHealthUpWhenKafkaIsReachable() {
        Map<String, Object> props = new HashMap<>();
        props.put("bootstrap.servers", "localhost:9092");
        when(kafkaAdmin.getConfigurationProperties()).thenReturn(props);

        Node controllerNode = new Node(1, "localhost", 9092);
        when(describeClusterResult.clusterId()).thenReturn(KafkaFuture.completedFuture("test-cluster-id"));
        when(describeClusterResult.controller()).thenReturn(KafkaFuture.completedFuture(controllerNode));
        when(describeClusterResult.nodes()).thenReturn(KafkaFuture.completedFuture(Collections.singletonList(controllerNode)));

        when(adminClient.describeCluster(any(DescribeClusterOptions.class))).thenReturn(describeClusterResult);

        KafkaHealthIndicator indicator = new KafkaHealthIndicator(kafkaAdmin, p -> adminClient);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("clusterId", "test-cluster-id");
        assertThat(health.getDetails()).containsEntry("nodesCount", 1);
        assertThat(health.getDetails()).containsEntry("controller", "localhost:9092");
    }

    @Test
    @DisplayName("health() returns DOWN when Kafka admin call times out or fails")
    void shouldReturnHealthDownWhenKafkaFails() {
        Map<String, Object> props = new HashMap<>();
        props.put("bootstrap.servers", "localhost:9092");
        when(kafkaAdmin.getConfigurationProperties()).thenReturn(props);

        KafkaHealthIndicator indicator = new KafkaHealthIndicator(kafkaAdmin, p -> {
            throw new RuntimeException("Connection refused");
        });
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
        assertThat(health.getDetails().get("error").toString()).contains("Connection refused");
    }
}
