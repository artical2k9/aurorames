package com.mes.auditservice.unit.health;

import com.mes.auditservice.health.KafkaConsumerLagHealthIndicator;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaConsumerLagHealthIndicatorTest {

    @Mock
    private AdminClient adminClient;

    @InjectMocks
    private KafkaConsumerLagHealthIndicator indicator;

    @Test
    void returnsUpWhenLagBelowThreshold() {
        ReflectionTestUtils.setField(indicator, "lagThreshold", 1000L);
        TopicPartition tp = new TopicPartition("mes.audit.events", 0);

        mockLag(tp, 100L, 600L);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("lag");
    }

    @Test
    void returnsDownWhenLagExceedsThreshold() {
        ReflectionTestUtils.setField(indicator, "lagThreshold", 1000L);
        TopicPartition tp = new TopicPartition("mes.audit.events", 0);

        mockLag(tp, 100L, 1600L);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @SuppressWarnings("unchecked")
    private void mockLag(TopicPartition tp, long committed, long endOffset) {
        ListConsumerGroupOffsetsResult groupOffsetsResult =
            mock(ListConsumerGroupOffsetsResult.class);
        when(adminClient.listConsumerGroupOffsets(anyString())).thenReturn(groupOffsetsResult);

        Map<TopicPartition, OffsetAndMetadata> committedOffsets =
            Map.of(tp, new OffsetAndMetadata(committed));
        when(groupOffsetsResult.partitionsToOffsetAndMetadata())
            .thenReturn(KafkaFuture.completedFuture(committedOffsets));

        ListOffsetsResult listOffsetsResult = mock(ListOffsetsResult.class);
        when(adminClient.listOffsets(any(Map.class), any())).thenReturn(listOffsetsResult);

        ListOffsetsResult.ListOffsetsResultInfo offsetInfo =
            mock(ListOffsetsResult.ListOffsetsResultInfo.class);
        when(offsetInfo.offset()).thenReturn(endOffset);
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsets =
            Map.of(tp, offsetInfo);
        when(listOffsetsResult.all())
            .thenReturn(KafkaFuture.completedFuture(endOffsets));
    }
}
