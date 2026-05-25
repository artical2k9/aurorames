package com.mes.keycloak.audit;

import com.mes.events.audit.AuditEventMessage;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KafkaAuditPublisherTest {

    @Mock
    private Producer<String, String> producer;

    private KafkaAuditPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new KafkaAuditPublisher(producer, "mes.audit.events");
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishSendsProducerRecordToCorrectTopic() {
        AuditEventMessage message = new AuditEventMessage(
            UUID.randomUUID(),
            "AUTH_EVENT",
            "keycloak-spi",
            "USER",
            "user-abc-123",
            "user-abc-123",
            OffsetDateTime.now(),
            "AUTH",
            null,
            1
        );

        publisher.publish(message);

        ArgumentCaptor<ProducerRecord<String, String>> captor =
            ArgumentCaptor.forClass(ProducerRecord.class);
        verify(producer).send(captor.capture());

        assertThat(captor.getValue().topic()).isEqualTo("mes.audit.events");
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishIncludesNonNullEventIdInPayload() {
        UUID eventId = UUID.randomUUID();
        AuditEventMessage message = new AuditEventMessage(
            eventId,
            "AUTH_EVENT",
            "keycloak-spi",
            "USER",
            "user-abc-123",
            "user-abc-123",
            OffsetDateTime.now(),
            "AUTH",
            null,
            1
        );

        publisher.publish(message);

        ArgumentCaptor<ProducerRecord<String, String>> captor =
            ArgumentCaptor.forClass(ProducerRecord.class);
        verify(producer).send(captor.capture());

        String payload = captor.getValue().value();
        assertThat(payload).contains(eventId.toString());
    }
}
