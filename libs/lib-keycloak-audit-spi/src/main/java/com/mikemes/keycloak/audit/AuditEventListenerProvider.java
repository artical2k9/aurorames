package com.mikemes.keycloak.audit;

import com.mikemes.events.audit.AuditEventMessage;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public class AuditEventListenerProvider implements EventListenerProvider {

    private static final String EVENT_TYPE_AUTH = "AUTH_EVENT";
    private static final String ACTION_AUTH = "AUTH";
    private static final String ENTITY_TYPE_USER = "USER";
    private static final String ENTITY_TYPE_REALM = "REALM";
    private static final String SERVICE_SOURCE = "keycloak-spi";
    private static final int SCHEMA_VERSION = 1;

    private final KafkaAuditPublisher publisher;

    public AuditEventListenerProvider(KafkaAuditPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void onEvent(Event event) {
        if (event.getUserId() == null) {
            return;
        }
        Map<String, Object> details = buildDetails(event.getDetails());
        AuditEventMessage message = new AuditEventMessage(
            UUID.randomUUID(),
            EVENT_TYPE_AUTH,
            SERVICE_SOURCE,
            ENTITY_TYPE_USER,
            event.getUserId(),
            event.getUserId(),
            toOffsetDateTime(event.getTime()),
            ACTION_AUTH,
            details,
            SCHEMA_VERSION
        );
        publisher.publish(message);
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {
        if (adminEvent.getAuthDetails() == null) {
            return;
        }
        String userId = adminEvent.getAuthDetails().getUserId();
        if (userId == null) {
            return;
        }
        String action = mapOperationType(adminEvent.getOperationType());
        AuditEventMessage message = new AuditEventMessage(
            UUID.randomUUID(),
            EVENT_TYPE_AUTH,
            SERVICE_SOURCE,
            ENTITY_TYPE_REALM,
            adminEvent.getRealmId(),
            userId,
            toOffsetDateTime(adminEvent.getTime()),
            action,
            Collections.emptyMap(),
            SCHEMA_VERSION
        );
        publisher.publish(message);
    }

    @Override
    public void close() {
        // lifecycle managed by factory
    }

    private OffsetDateTime toOffsetDateTime(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atOffset(ZoneOffset.UTC);
    }

    private Map<String, Object> buildDetails(Map<String, String> keycloakDetails) {
        if (keycloakDetails == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(keycloakDetails);
    }

    private String mapOperationType(OperationType type) {
        if (type == null) {
            return "ADMIN";
        }
        if (type == OperationType.CREATE) {
            return "CREATE";
        }
        if (type == OperationType.UPDATE) {
            return "UPDATE";
        }
        if (type == OperationType.DELETE) {
            return "DELETE";
        }
        return "ACTION";
    }
}
