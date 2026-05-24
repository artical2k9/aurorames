package com.mikemes.audit.checksum;

import com.mikemes.audit.domain.AuditRecord;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChecksumServiceTest {

    private final ChecksumService service = new ChecksumService();

    private AuditRecord buildRecord() {
        return AuditRecord.builder()
            .id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
            .eventId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
            .eventType("KAFKA_EVENT")
            .entityType("WorkOrder")
            .entityId("WO-001")
            .userId("user:abc")
            .serviceSource("work-order-service")
            .action("CREATE")
            .occurredAt(OffsetDateTime.of(2026, 5, 24, 10, 0, 0, 0, ZoneOffset.UTC))
            .schemaVersion(1)
            .build();
    }

    @Test
    void computeReturns64CharHexString() {
        String checksum = service.compute(buildRecord());

        assertThat(checksum).hasSize(64);
        assertThat(checksum).matches("[a-f0-9]{64}");
    }

    @Test
    void verifyReturnsTrueForIntactRecord() {
        AuditRecord record = buildRecord();
        String checksum = service.compute(record);
        AuditRecord withChecksum = AuditRecord.builder()
            .id(record.getId())
            .eventId(record.getEventId())
            .eventType(record.getEventType())
            .entityType(record.getEntityType())
            .entityId(record.getEntityId())
            .userId(record.getUserId())
            .serviceSource(record.getServiceSource())
            .action(record.getAction())
            .occurredAt(record.getOccurredAt())
            .schemaVersion(record.getSchemaVersion())
            .checksum(checksum)
            .build();

        assertThat(service.verify(withChecksum)).isTrue();
    }

    @Test
    void verifyReturnsFalseWhenUserIdMutated() {
        AuditRecord original = buildRecord();
        String checksum = service.compute(original);

        AuditRecord tampered = AuditRecord.builder()
            .id(original.getId())
            .eventId(original.getEventId())
            .eventType(original.getEventType())
            .entityType(original.getEntityType())
            .entityId(original.getEntityId())
            .userId("user:attacker")  // mutated
            .serviceSource(original.getServiceSource())
            .action(original.getAction())
            .occurredAt(original.getOccurredAt())
            .schemaVersion(original.getSchemaVersion())
            .checksum(checksum)  // original checksum
            .build();

        assertThat(service.verify(tampered)).isFalse();
    }

    @Test
    void sameInputProducesSameChecksum() {
        AuditRecord r1 = buildRecord();
        AuditRecord r2 = buildRecord();

        assertThat(service.compute(r1)).isEqualTo(service.compute(r2));
    }

    @Test
    void checksumChangesWhenPayloadChanges() {
        AuditRecord withoutPayload = buildRecord();
        AuditRecord withPayload = AuditRecord.builder()
            .id(withoutPayload.getId())
            .eventId(withoutPayload.getEventId())
            .eventType(withoutPayload.getEventType())
            .entityType(withoutPayload.getEntityType())
            .entityId(withoutPayload.getEntityId())
            .userId(withoutPayload.getUserId())
            .serviceSource(withoutPayload.getServiceSource())
            .action(withoutPayload.getAction())
            .occurredAt(withoutPayload.getOccurredAt())
            .schemaVersion(withoutPayload.getSchemaVersion())
            .newState(Map.of("status", "OPEN"))
            .build();

        assertThat(service.compute(withoutPayload)).isNotEqualTo(service.compute(withPayload));
    }
}
