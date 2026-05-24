package com.mikemes.audit.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Immutable
@Table(schema = "audit", name = "audit_records")
public class AuditRecord {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 100)
    private String entityId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "service_source", nullable = false, length = 100)
    private String serviceSource;

    @Column(name = "action", nullable = false, length = 20)
    private String action;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Type(JsonBinaryType.class)
    @Column(name = "previous_state", columnDefinition = "jsonb")
    private Map<String, Object> previousState;

    @Type(JsonBinaryType.class)
    @Column(name = "new_state", columnDefinition = "jsonb")
    private Map<String, Object> newState;

    @Column(name = "checksum", nullable = false, length = 64)
    private String checksum;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(name = "tenant_id")
    private UUID tenantId;

    protected AuditRecord() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getUserId() {
        return userId;
    }

    public String getServiceSource() {
        return serviceSource;
    }

    public String getAction() {
        return action;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public Map<String, Object> getPreviousState() {
        return previousState;
    }

    public Map<String, Object> getNewState() {
        return newState;
    }

    public String getChecksum() {
        return checksum;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final AuditRecord record = new AuditRecord();

        public Builder id(UUID id) {
            record.id = id;
            return this;
        }

        public Builder eventId(UUID eventId) {
            record.eventId = eventId;
            return this;
        }

        public Builder eventType(String eventType) {
            record.eventType = eventType;
            return this;
        }

        public Builder entityType(String entityType) {
            record.entityType = entityType;
            return this;
        }

        public Builder entityId(String entityId) {
            record.entityId = entityId;
            return this;
        }

        public Builder userId(String userId) {
            record.userId = userId;
            return this;
        }

        public Builder serviceSource(String serviceSource) {
            record.serviceSource = serviceSource;
            return this;
        }

        public Builder action(String action) {
            record.action = action;
            return this;
        }

        public Builder occurredAt(OffsetDateTime occurredAt) {
            record.occurredAt = occurredAt;
            return this;
        }

        public Builder previousState(Map<String, Object> previousState) {
            record.previousState = previousState;
            return this;
        }

        public Builder newState(Map<String, Object> newState) {
            record.newState = newState;
            return this;
        }

        public Builder checksum(String checksum) {
            record.checksum = checksum;
            return this;
        }

        public Builder schemaVersion(int schemaVersion) {
            record.schemaVersion = schemaVersion;
            return this;
        }

        public Builder tenantId(UUID tenantId) {
            record.tenantId = tenantId;
            return this;
        }

        public AuditRecord build() {
            return record;
        }
    }
}
