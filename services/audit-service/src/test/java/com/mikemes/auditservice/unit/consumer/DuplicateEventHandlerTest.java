package com.mikemes.auditservice.unit.consumer;

import com.mikemes.auditservice.consumer.DuplicateEventHandler;
import com.mikemes.events.audit.AuditEventMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DuplicateEventHandlerTest {

    @InjectMocks
    private DuplicateEventHandler handler;

    @Mock
    private Acknowledgment acknowledgment;

    @Test
    void handleDuplicateDoesNotThrow() {
        AuditEventMessage message = new AuditEventMessage(
            UUID.randomUUID(), "KAFKA_EVENT", "test-service",
            "WorkOrder", "WO-001", "user:test",
            OffsetDateTime.now(ZoneOffset.UTC), "CREATE",
            Map.of(), 1
        );

        assertThatNoException().isThrownBy(() -> handler.handleDuplicate(message, acknowledgment));
    }

    @Test
    void handleDuplicateAcknowledgesMessage() {
        AuditEventMessage message = new AuditEventMessage(
            UUID.randomUUID(), "KAFKA_EVENT", "test-service",
            "WorkOrder", "WO-001", "user:test",
            OffsetDateTime.now(ZoneOffset.UTC), "CREATE",
            Map.of(), 1
        );

        handler.handleDuplicate(message, acknowledgment);

        verify(acknowledgment).acknowledge();
    }
}
