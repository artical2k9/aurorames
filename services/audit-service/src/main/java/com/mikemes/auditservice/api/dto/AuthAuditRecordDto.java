package com.mikemes.auditservice.api.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record AuthAuditRecordDto(
        UUID id,
        UUID eventId,
        String eventType,
        String userId,
        String clientId,
        String ipAddress,
        String sessionId,
        String realmId,
        OffsetDateTime occurredAt,
        Map<String, Object> details
) {
}
