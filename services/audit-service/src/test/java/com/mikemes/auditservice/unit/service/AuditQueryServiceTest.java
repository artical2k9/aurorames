package com.mikemes.auditservice.unit.service;

import com.mikemes.audit.domain.AuditRecord;
import com.mikemes.audit.domain.AuthAuditRecord;
import com.mikemes.auditservice.api.dto.AuditRecordDto;
import com.mikemes.auditservice.api.dto.AuthAuditRecordDto;
import com.mikemes.auditservice.repository.AuditRecordRepository;
import com.mikemes.auditservice.repository.AuthAuditRecordRepository;
import com.mikemes.auditservice.service.AuditQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditQueryServiceTest {

    @Mock
    private AuditRecordRepository auditRepository;

    @Mock
    private AuthAuditRecordRepository authRepository;

    @InjectMocks
    private AuditQueryService service;

    @Test
    void findEntityHistoryCallsRepositoryWithCorrectArgs() {
        OffsetDateTime from = OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime to = OffsetDateTime.of(2025, 1, 8, 0, 0, 0, 0, ZoneOffset.UTC);
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "occurredAt"));

        AuditRecord record = AuditRecord.builder()
            .id(UUID.randomUUID())
            .eventId(UUID.randomUUID())
            .eventType("KAFKA_EVENT")
            .entityType("WorkOrder")
            .entityId("id-1")
            .userId("user:test")
            .serviceSource("work-order-service")
            .action("CREATE")
            .occurredAt(from.plusHours(1))
            .schemaVersion(1)
            .build();

        when(auditRepository.findByEntityTypeAndEntityIdAndOccurredAtBetween(
            eq("WorkOrder"), eq("id-1"), eq(from), eq(to), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(record)));

        Page<AuditRecordDto> result = service.findEntityHistory("WorkOrder", "id-1", from, to, pageable);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditRepository).findByEntityTypeAndEntityIdAndOccurredAtBetween(
            eq("WorkOrder"), eq("id-1"), eq(from), eq(to), pageableCaptor.capture());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).entityType()).isEqualTo("WorkOrder");
        assertThat(result.getContent().get(0).entityId()).isEqualTo("id-1");
        assertThat(result.getContent().get(0).userId()).isEqualTo("user:test");
    }

    @Test
    void findAuditRecordsCallsRepositoryWithFilters() {
        OffsetDateTime from = OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime to = OffsetDateTime.of(2025, 1, 8, 0, 0, 0, 0, ZoneOffset.UTC);
        Pageable pageable = PageRequest.of(0, 20);

        when(auditRepository.findByFilters(any(), any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of()));

        Page<AuditRecordDto> result = service.findAuditRecords(
            "WorkOrder", "user:test", "CREATE", from, to, pageable);

        assertThat(result.getContent()).isEmpty();
        verify(auditRepository).findByFilters(
            eq("WorkOrder"), eq("user:test"), eq("CREATE"), eq(from), eq(to), any(Pageable.class));
    }

    @Test
    void findAuthEventsCallsAuthRepositoryAndMapsToDto() {
        OffsetDateTime from = OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime to = OffsetDateTime.of(2025, 1, 8, 0, 0, 0, 0, ZoneOffset.UTC);
        Pageable pageable = PageRequest.of(0, 20);
        UUID recordId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        AuthAuditRecord record = AuthAuditRecord.builder()
            .id(recordId)
            .eventId(eventId)
            .eventType("AUTH_EVENT")
            .userId("user:auth-test")
            .clientId("mes-frontend")
            .ipAddress("10.0.0.1")
            .sessionId("session-001")
            .realmId("mikemes")
            .occurredAt(from.plusHours(1))
            .build();

        when(authRepository.findByFilters(any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(record)));

        Page<AuthAuditRecordDto> result = service.findAuthEvents(
            "user:auth-test", "AUTH_EVENT", from, to, pageable);

        assertThat(result.getContent()).hasSize(1);
        AuthAuditRecordDto dto = result.getContent().get(0);
        assertThat(dto.id()).isEqualTo(recordId);
        assertThat(dto.eventId()).isEqualTo(eventId);
        assertThat(dto.userId()).isEqualTo("user:auth-test");
        assertThat(dto.clientId()).isEqualTo("mes-frontend");
        assertThat(dto.realmId()).isEqualTo("mikemes");
    }

    @Test
    void findEntityHistoryMapsRecordToDto() {
        UUID recordId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        OffsetDateTime from = OffsetDateTime.now(ZoneOffset.UTC).minusDays(7);
        OffsetDateTime to = OffsetDateTime.now(ZoneOffset.UTC);
        Pageable pageable = PageRequest.of(0, 20);

        AuditRecord record = AuditRecord.builder()
            .id(recordId)
            .eventId(eventId)
            .eventType("KAFKA_EVENT")
            .entityType("WorkOrder")
            .entityId("id-1")
            .userId("user:test")
            .serviceSource("svc")
            .action("UPDATE")
            .occurredAt(from.plusHours(2))
            .checksum("a".repeat(64))
            .schemaVersion(1)
            .build();

        when(auditRepository.findByEntityTypeAndEntityIdAndOccurredAtBetween(
            any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(record)));

        Page<AuditRecordDto> result = service.findEntityHistory("WorkOrder", "id-1", from, to, pageable);

        AuditRecordDto dto = result.getContent().get(0);
        assertThat(dto.id()).isEqualTo(recordId);
        assertThat(dto.eventId()).isEqualTo(eventId);
        assertThat(dto.checksum()).isEqualTo("a".repeat(64));
        assertThat(dto.action()).isEqualTo("UPDATE");
    }
}
