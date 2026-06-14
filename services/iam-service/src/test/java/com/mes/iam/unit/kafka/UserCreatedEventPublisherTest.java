package com.mes.iam.unit.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mes.iam.kafka.UserCreatedEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link UserCreatedEventPublisher} — payload assembly, null-safe ternaries
 * and the serialization-failure catch branch (MES-117).
 */
@ExtendWith(MockitoExtension.class)
class UserCreatedEventPublisherTest {

    private static final String TOPIC = "iam.user.created";
    private static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock KafkaTemplate<Object, Object> kafkaTemplate;

    @Test
    void publishUserCreated_sendsJsonKeyedByUserId() {
        UserCreatedEventPublisher publisher =
                new UserCreatedEventPublisher(kafkaTemplate, TOPIC, new ObjectMapper());

        publisher.publishUserCreated("kc-1", ORG_ID, "ada@test.com", "Ada", "Lovelace",
                "EMP-001", LocalDate.of(2026, 6, 1));

        ArgumentCaptorHolder holder = capture();
        assertThat(holder.payload).contains("\"userId\":\"kc-1\"");
        assertThat(holder.payload).contains("\"orgId\":\"00000000-0000-0000-0000-000000000001\"");
        assertThat(holder.payload).contains("\"employeeNumber\":\"EMP-001\"");
        assertThat(holder.payload).contains("\"hireDate\":\"2026-06-01\"");
    }

    @Test
    void publishUserCreated_nullOrgIdAndHireDate_serialisedAsNull() {
        UserCreatedEventPublisher publisher =
                new UserCreatedEventPublisher(kafkaTemplate, TOPIC, new ObjectMapper());

        publisher.publishUserCreated("kc-2", null, "a@b.com", "A", "B", "EMP-002", null);

        ArgumentCaptorHolder holder = capture();
        assertThat(holder.payload).contains("\"orgId\":null");
        assertThat(holder.payload).contains("\"hireDate\":null");
    }

    @Test
    void publishUserCreated_serializationFailure_isSwallowed() throws JsonProcessingException {
        ObjectMapper failing = org.mockito.Mockito.mock(ObjectMapper.class);
        when(failing.writeValueAsString(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new JsonProcessingException("boom") { });
        UserCreatedEventPublisher publisher =
                new UserCreatedEventPublisher(kafkaTemplate, TOPIC, failing);

        publisher.publishUserCreated("kc-3", ORG_ID, "a@b.com", "A", "B", "EMP-003", null);

        verify(kafkaTemplate, never()).send(eq(TOPIC), eq("kc-3"), org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(kafkaTemplate);
    }

    private ArgumentCaptorHolder capture() {
        var keyCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
        var valueCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(TOPIC), keyCaptor.capture(), valueCaptor.capture());
        ArgumentCaptorHolder holder = new ArgumentCaptorHolder();
        holder.key = String.valueOf(keyCaptor.getValue());
        holder.payload = String.valueOf(valueCaptor.getValue());
        return holder;
    }

    private static final class ArgumentCaptorHolder {
        String key;
        String payload;
    }
}
