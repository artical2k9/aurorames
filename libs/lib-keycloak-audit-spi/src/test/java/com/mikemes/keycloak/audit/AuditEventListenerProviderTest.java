package com.mikemes.keycloak.audit;

import com.mikemes.events.audit.AuditEventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditEventListenerProviderTest {

    @Mock
    private KafkaAuditPublisher publisher;

    private AuditEventListenerProvider provider;

    @BeforeEach
    void setUp() {
        provider = new AuditEventListenerProvider(publisher);
    }

    @Test
    void onLoginEventPublishesAuthEventMessage() {
        Event event = new Event();
        event.setUserId("user-abc-123");
        event.setType(EventType.LOGIN);
        event.setTime(System.currentTimeMillis());
        event.setClientId("mes-frontend");
        event.setSessionId("session-xyz");

        provider.onEvent(event);

        ArgumentCaptor<AuditEventMessage> captor = ArgumentCaptor.forClass(AuditEventMessage.class);
        verify(publisher).publish(captor.capture());

        AuditEventMessage msg = captor.getValue();
        assertThat(msg.eventType()).isEqualTo("AUTH_EVENT");
        assertThat(msg.action()).isEqualTo("AUTH");
        assertThat(msg.entityType()).isEqualTo("USER");
        assertThat(msg.userId()).isEqualTo("user-abc-123");
    }

    @Test
    void onLoginEventSetsUserIdFromEvent() {
        String expectedUserId = "user-def-456";
        Event event = new Event();
        event.setUserId(expectedUserId);
        event.setType(EventType.LOGIN);
        event.setTime(System.currentTimeMillis());

        provider.onEvent(event);

        ArgumentCaptor<AuditEventMessage> captor = ArgumentCaptor.forClass(AuditEventMessage.class);
        verify(publisher).publish(captor.capture());

        assertThat(captor.getValue().userId()).isEqualTo(expectedUserId);
    }

    @Test
    void onLoginEventSetsNonNullEventId() {
        Event event = new Event();
        event.setUserId("user-test");
        event.setType(EventType.LOGIN);
        event.setTime(System.currentTimeMillis());

        provider.onEvent(event);

        ArgumentCaptor<AuditEventMessage> captor = ArgumentCaptor.forClass(AuditEventMessage.class);
        verify(publisher).publish(captor.capture());

        assertThat(captor.getValue().eventId()).isNotNull();
    }
}
