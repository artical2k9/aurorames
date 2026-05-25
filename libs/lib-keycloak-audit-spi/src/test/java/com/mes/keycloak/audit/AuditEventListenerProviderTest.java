package com.mes.keycloak.audit;

import com.mes.events.audit.AuditEventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.AuthDetails;
import org.keycloak.events.admin.OperationType;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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

    @Test
    void onEventWithNullUserIdDoesNotPublish() {
        Event event = new Event();
        event.setUserId(null);
        event.setType(EventType.LOGIN);
        event.setTime(System.currentTimeMillis());

        provider.onEvent(event);

        verifyNoInteractions(publisher);
    }

    @Test
    void onAdminEventPublishesWithCorrectAction() {
        AuthDetails authDetails = new AuthDetails();
        authDetails.setUserId("admin-user-001");

        AdminEvent adminEvent = new AdminEvent();
        adminEvent.setAuthDetails(authDetails);
        adminEvent.setOperationType(OperationType.CREATE);
        adminEvent.setRealmId("mes");
        adminEvent.setTime(System.currentTimeMillis());

        provider.onEvent(adminEvent, false);

        ArgumentCaptor<AuditEventMessage> captor = ArgumentCaptor.forClass(AuditEventMessage.class);
        verify(publisher).publish(captor.capture());

        AuditEventMessage msg = captor.getValue();
        assertThat(msg.action()).isEqualTo("CREATE");
        assertThat(msg.userId()).isEqualTo("admin-user-001");
        assertThat(msg.entityType()).isEqualTo("REALM");
    }

    @Test
    void onAdminEventWithUpdateOperationPublishesUpdateAction() {
        AuthDetails authDetails = new AuthDetails();
        authDetails.setUserId("admin-user-002");

        AdminEvent adminEvent = new AdminEvent();
        adminEvent.setAuthDetails(authDetails);
        adminEvent.setOperationType(OperationType.UPDATE);
        adminEvent.setRealmId("mes");
        adminEvent.setTime(System.currentTimeMillis());

        provider.onEvent(adminEvent, false);

        ArgumentCaptor<AuditEventMessage> captor = ArgumentCaptor.forClass(AuditEventMessage.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo("UPDATE");
    }

    @Test
    void onAdminEventWithDeleteOperationPublishesDeleteAction() {
        AuthDetails authDetails = new AuthDetails();
        authDetails.setUserId("admin-user-003");

        AdminEvent adminEvent = new AdminEvent();
        adminEvent.setAuthDetails(authDetails);
        adminEvent.setOperationType(OperationType.DELETE);
        adminEvent.setRealmId("mes");
        adminEvent.setTime(System.currentTimeMillis());

        provider.onEvent(adminEvent, false);

        ArgumentCaptor<AuditEventMessage> captor = ArgumentCaptor.forClass(AuditEventMessage.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo("DELETE");
    }

    @Test
    void onAdminEventWithNullAuthDetailsDoesNotPublish() {
        AdminEvent adminEvent = new AdminEvent();
        adminEvent.setAuthDetails(null);
        adminEvent.setRealmId("mes");
        adminEvent.setTime(System.currentTimeMillis());

        provider.onEvent(adminEvent, false);

        verifyNoInteractions(publisher);
    }

    @Test
    void onAdminEventWithNullUserIdDoesNotPublish() {
        AuthDetails authDetails = new AuthDetails();
        authDetails.setUserId(null);

        AdminEvent adminEvent = new AdminEvent();
        adminEvent.setAuthDetails(authDetails);
        adminEvent.setRealmId("mes");
        adminEvent.setTime(System.currentTimeMillis());

        provider.onEvent(adminEvent, false);

        verifyNoInteractions(publisher);
    }

    @Test
    void closeDoesNotThrow() {
        provider.close();
    }
}
