package com.mes.auditservice.unit.consumer;

import com.mes.audit.checksum.ChecksumService;
import com.mes.audit.domain.AuthAuditRecord;
import com.mes.auditservice.consumer.AuditKafkaConsumer;
import com.mes.auditservice.consumer.DuplicateEventHandler;
import com.mes.auditservice.repository.AuditRecordRepository;
import com.mes.auditservice.repository.AuthAuditRecordRepository;
import com.mes.events.audit.AuditEventMessage;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthEventHandlerTest {

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
    void authEventRoutesToAuthAuditRecordRepository() {
        AuditEventMessage message = new AuditEventMessage(
            UUID.randomUUID(),
            "AUTH_EVENT",
            "keycloak-spi",
            "USER",
            "user-auth-001",
            "user-auth-001",
            OffsetDateTime.now(ZoneOffset.UTC),
            "AUTH",
            Map.of("client_id", "mes-frontend"),
            1
        );

        consumer.consume(message, acknowledgment);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<AuthAuditRecord> captor = ArgumentCaptor.forClass(AuthAuditRecord.class);
        verify(authRepository).save(captor.capture());
        verify(acknowledgment).acknowledge();
        assertThat(captor.getValue().getEventId()).isEqualTo(message.eventId());
        assertThat(captor.getValue().getUserId()).isEqualTo("user-auth-001");
    }

    @Test
    void authEventDoesNotRouteToAuditRecordRepository() {
        AuditEventMessage message = new AuditEventMessage(
            UUID.randomUUID(),
            "AUTH_EVENT",
            "keycloak-spi",
            "USER",
            "user-auth-002",
            "user-auth-002",
            OffsetDateTime.now(ZoneOffset.UTC),
            "AUTH",
            Map.of(),
            1
        );

        consumer.consume(message, acknowledgment);

        verify(repository, never()).save(any());
    }
}
