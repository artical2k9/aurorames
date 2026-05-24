package com.mikemes.events.audit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record AuditEventMessage(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("serviceSource") String serviceSource,
        @JsonProperty("entityType") String entityType,
        @JsonProperty("entityId") String entityId,
        @JsonProperty("userId") String userId,
        @JsonProperty("timestamp") OffsetDateTime timestamp,
        @JsonProperty("action") String action,
        @JsonProperty("payload") Map<String, Object> payload,
        @JsonProperty("schemaVersion") int schemaVersion
) {

    @JsonCreator
    public AuditEventMessage {
        // compact canonical constructor — validation at boundary
        if (eventId == null) {
            throw new IllegalArgumentException("eventId must not be null");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException("entityType must not be blank");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
    }
}
