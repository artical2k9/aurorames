package com.mes.events.audit;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditEventMessageTest {

    @Test
    void constructorAcceptsValidMessage() {
        AuditEventMessage msg = new AuditEventMessage(
            UUID.randomUUID(), "AUTH_EVENT", "svc", "USER",
            "entity-1", "user:test",
            OffsetDateTime.now(ZoneOffset.UTC), "CREATE",
            Map.of(), 1
        );
        assertThat(msg.eventId()).isNotNull();
        assertThat(msg.eventType()).isEqualTo("AUTH_EVENT");
        assertThat(msg.userId()).isEqualTo("user:test");
    }

    @Test
    void constructorRejectsNullEventId() {
        assertThatThrownBy(() -> new AuditEventMessage(
            null, "AUTH_EVENT", "svc", "USER",
            "entity-1", "user:test",
            OffsetDateTime.now(ZoneOffset.UTC), "CREATE",
            Map.of(), 1
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("eventId");
    }

    @Test
    void constructorRejectsBlankEventType() {
        assertThatThrownBy(() -> new AuditEventMessage(
            UUID.randomUUID(), "", "svc", "USER",
            "entity-1", "user:test",
            OffsetDateTime.now(ZoneOffset.UTC), "CREATE",
            Map.of(), 1
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("eventType");
    }

    @Test
    void constructorRejectsBlankEntityType() {
        assertThatThrownBy(() -> new AuditEventMessage(
            UUID.randomUUID(), "AUTH_EVENT", "svc", "",
            "entity-1", "user:test",
            OffsetDateTime.now(ZoneOffset.UTC), "CREATE",
            Map.of(), 1
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("entityType");
    }

    @Test
    void constructorRejectsBlankUserId() {
        assertThatThrownBy(() -> new AuditEventMessage(
            UUID.randomUUID(), "AUTH_EVENT", "svc", "USER",
            "entity-1", "  ",
            OffsetDateTime.now(ZoneOffset.UTC), "CREATE",
            Map.of(), 1
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("userId");
    }
}
