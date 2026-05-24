package com.mikemes.auditservice.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsOptions;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Component
public class KafkaConsumerLagHealthIndicator implements HealthIndicator {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumerLagHealthIndicator.class);
    private static final long TIMEOUT_SECONDS = 5L;
    private static final String CONSUMER_GROUP = "audit-service-group";

    private final AdminClient adminClient;

    @Value("${audit.health.kafka-lag-threshold:1000}")
    private long lagThreshold;

    public KafkaConsumerLagHealthIndicator(AdminClient adminClient) {
        this.adminClient = adminClient;
    }

    @Override
    public Health health() {
        try {
            long totalLag = computeTotalLag();
            if (totalLag <= lagThreshold) {
                return Health.up()
                    .withDetail("lag", totalLag)
                    .withDetail("threshold", lagThreshold)
                    .build();
            }
            return Health.down()
                .withDetail("lag", totalLag)
                .withDetail("threshold", lagThreshold)
                .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while computing Kafka consumer lag", e);
            return Health.down(e).build();
        } catch (ExecutionException | TimeoutException e) {
            LOG.warn("Failed to compute Kafka consumer lag", e);
            return Health.down(e).build();
        }
    }

    private long computeTotalLag()
            throws ExecutionException, InterruptedException, TimeoutException {
        Map<TopicPartition, OffsetAndMetadata> committedOffsets =
            adminClient.listConsumerGroupOffsets(CONSUMER_GROUP)
                .partitionsToOffsetAndMetadata()
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (committedOffsets.isEmpty()) {
            return 0L;
        }

        Map<TopicPartition, OffsetSpec> latestOffsetRequest = committedOffsets.keySet().stream()
            .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));

        Map<TopicPartition, Long> endOffsets =
            adminClient.listOffsets(latestOffsetRequest, new ListOffsetsOptions())
                .all()
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().offset()));

        return committedOffsets.entrySet().stream()
            .mapToLong(entry -> {
                long end = endOffsets.getOrDefault(entry.getKey(), 0L);
                long committed = entry.getValue().offset();
                return Math.max(0, end - committed);
            })
            .sum();
    }
}
