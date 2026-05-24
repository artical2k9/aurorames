package com.mikemes.auditservice.unit.service;

import com.mikemes.audit.checksum.ChecksumService;
import com.mikemes.audit.domain.AuditRecord;
import com.mikemes.auditservice.api.dto.VerificationResult;
import com.mikemes.auditservice.repository.AuditRecordRepository;
import com.mikemes.auditservice.service.TamperVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TamperVerificationServiceTest {

    @Mock
    private AuditRecordRepository repository;

    private ChecksumService checksumService;
    private TamperVerificationService service;

    private final OffsetDateTime from = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
    private final OffsetDateTime to = OffsetDateTime.now(ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        checksumService = new ChecksumService();
        service = new TamperVerificationService(repository, checksumService);
    }

    @Test
    void verifyReturnsPassWhenAllChecksumsMatch() {
        List<AuditRecord> records = buildRecordsWithCorrectChecksums(5);
        when(repository.findByOccurredAtBetween(eq(from), eq(to),
                eq(PageRequest.of(0, 500, Sort.by("occurredAt")))))
            .thenReturn(new PageImpl<>(records));

        VerificationResult result = service.verify(from, to);

        assertThat(result.status()).isEqualTo("PASS");
        assertThat(result.recordsChecked()).isEqualTo(5);
        assertThat(result.violations()).isEmpty();
        assertThat(result.verifiedAt()).isNotNull();
    }

    @Test
    void verifyReturnsFailWithOneViolationWhenChecksumMismatches() {
        List<AuditRecord> cleanRecords = buildRecordsWithCorrectChecksums(4);
        AuditRecord tamperedRecord = buildRecordWithWrongChecksum();
        List<AuditRecord> records = new java.util.ArrayList<>(cleanRecords);
        records.add(tamperedRecord);

        when(repository.findByOccurredAtBetween(eq(from), eq(to),
                eq(PageRequest.of(0, 500, Sort.by("occurredAt")))))
            .thenReturn(new PageImpl<>(records));

        VerificationResult result = service.verify(from, to);

        assertThat(result.status()).isEqualTo("FAIL");
        assertThat(result.recordsChecked()).isEqualTo(5);
        assertThat(result.violations()).hasSize(1);
        assertThat(result.violations().get(0).recordId()).isEqualTo(tamperedRecord.getId());
        assertThat(result.violations().get(0).reason()).isEqualTo("CHECKSUM_MISMATCH");
        assertThat(result.violations().get(0).actualChecksum()).isEqualTo("tampered-checksum");
    }

    private List<AuditRecord> buildRecordsWithCorrectChecksums(int count) {
        List<AuditRecord> records = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            AuditRecord base = AuditRecord.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .eventType("KAFKA_EVENT")
                .entityType("WorkOrder")
                .entityId("WO-00" + i)
                .userId("user-" + i)
                .serviceSource("test-service")
                .action("CREATE")
                .occurredAt(from.plusMinutes(i))
                .checksum("placeholder")
                .schemaVersion(1)
                .build();
            String checksum = checksumService.compute(base);
            records.add(AuditRecord.builder()
                .id(base.getId())
                .eventId(base.getEventId())
                .eventType(base.getEventType())
                .entityType(base.getEntityType())
                .entityId(base.getEntityId())
                .userId(base.getUserId())
                .serviceSource(base.getServiceSource())
                .action(base.getAction())
                .occurredAt(base.getOccurredAt())
                .checksum(checksum)
                .schemaVersion(base.getSchemaVersion())
                .build());
        }
        return records;
    }

    private AuditRecord buildRecordWithWrongChecksum() {
        return AuditRecord.builder()
            .id(UUID.randomUUID())
            .eventId(UUID.randomUUID())
            .eventType("KAFKA_EVENT")
            .entityType("WorkOrder")
            .entityId("WO-tampered")
            .userId("user-tampered")
            .serviceSource("test-service")
            .action("CREATE")
            .occurredAt(from.plusMinutes(10))
            .checksum("tampered-checksum")
            .schemaVersion(1)
            .build();
    }
}
