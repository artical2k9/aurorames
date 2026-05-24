package com.mikemes.auditservice.unit.consumer;

import com.mikemes.audit.checksum.ChecksumService;
import com.mikemes.audit.domain.AuditRecord;
import com.mikemes.auditservice.consumer.AuditKafkaConsumer;
import com.mikemes.auditservice.consumer.DuplicateEventHandler;
import com.mikemes.auditservice.repository.AuditRecordRepository;
import com.mikemes.auditservice.repository.AuthAuditRecordRepository;
import com.mikemes.events.audit.AuditEventMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditKafkaConsumerTest {

    @Mock
    private AuditRecordRepository repository;

    @Mock
    private AuthAuditRecordRepository authRepository;

    @Mock
    private ChecksumService checksumService;

    @Mock
    private DuplicateEventHandler duplicateEventHandler;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private AuditKafkaConsumer consumer;

    @Test
    void consumeSavesRecordAndAcknowledges() {
        AuditEventMessage message = new AuditEventMessage(
            UUID.randomUUID(), "KAFKA_EVENT", "test-service",
            "WorkOrder", "WO-001", "user:test",
            OffsetDateTime.now(ZoneOffset.UTC), "CREATE",
            Map.of("key", "value"), 1
        );
        when(checksumService.compute(any(AuditRecord.class))).thenReturn("a".repeat(64));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);

        consumer.consume(message, acknowledgment);

        verify(repository).save(captor.capture());
        verify(acknowledgment).acknowledge();
        AuditRecord saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(message.eventId());
        assertThat(saved.getEntityType()).isEqualTo("WorkOrder");
        assertThat(saved.getUserId()).isEqualTo("user:test");
    }

    @Test
    void consumeSetsChecksumFromChecksumService() {
        AuditEventMessage message = new AuditEventMessage(
            UUID.randomUUID(), "KAFKA_EVENT", "test-service",
            "WorkOrder", "WO-002", "user:test",
            OffsetDateTime.now(ZoneOffset.UTC), "UPDATE",
            Map.of(), 1
        );
        String expectedChecksum = "b".repeat(64);
        when(checksumService.compute(any(AuditRecord.class))).thenReturn(expectedChecksum);

        consumer.consume(message, acknowledgment);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getChecksum()).isEqualTo(expectedChecksum);
    }
}
