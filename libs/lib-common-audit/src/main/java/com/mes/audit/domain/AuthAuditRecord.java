package com.mes.audit.domain;

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
@Table(schema = "audit", name = "auth_audit_records")
public class AuthAuditRecord {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "user_id", length = 100)
    private String userId;

    @Column(name = "client_id", length = 100)
    private String clientId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "realm_id", nullable = false, length = 100)
    private String realmId;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "tenant_id")
    private UUID tenantId;

    protected AuthAuditRecord() {
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

    public String getUserId() {
        return userId;
    }

    public String getClientId() {
        return clientId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getRealmId() {
        return realmId;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final AuthAuditRecord record = new AuthAuditRecord();

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

        public Builder userId(String userId) {
            record.userId = userId;
            return this;
        }

        public Builder clientId(String clientId) {
            record.clientId = clientId;
            return this;
        }

        public Builder ipAddress(String ipAddress) {
            record.ipAddress = ipAddress;
            return this;
        }

        public Builder sessionId(String sessionId) {
            record.sessionId = sessionId;
            return this;
        }

        public Builder realmId(String realmId) {
            record.realmId = realmId;
            return this;
        }

        public Builder occurredAt(OffsetDateTime occurredAt) {
            record.occurredAt = occurredAt;
            return this;
        }

        public Builder details(Map<String, Object> details) {
            record.details = details;
            return this;
        }

        public Builder tenantId(UUID tenantId) {
            record.tenantId = tenantId;
            return this;
        }

        public AuthAuditRecord build() {
            return record;
        }
    }
}
