package com.mikemes.auditservice.api.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record AuditRecordDto(
        UUID id,
        UUID eventId,
        String eventType,
        String entityType,
        String entityId,
        String userId,
        String serviceSource,
        String action,
        OffsetDateTime occurredAt,
        Map<String, Object> previousState,
        Map<String, Object> newState,
        String checksum,
        int schemaVersion
) {
}
