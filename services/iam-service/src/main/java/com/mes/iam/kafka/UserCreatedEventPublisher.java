package com.mes.iam.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes {@code iam.user.created} so labour-service can create the linked employee record
 * (employee↔IAM unification — MES-117). Payload is a JSON string keyed by user id, matching the
 * iam-service String-serialised producer config.
 */
@Component
public class UserCreatedEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(UserCreatedEventPublisher.class);

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final String topic;
    private final ObjectMapper objectMapper;

    public UserCreatedEventPublisher(
            KafkaTemplate<Object, Object> kafkaTemplate,
            @Value("${iam.events.user-created-topic:iam.user.created}") String topic,
            ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.objectMapper = objectMapper;
    }

    public void publishUserCreated(String userId, UUID orgId, String email,
                                   String firstName, String lastName,
                                   String employeeNumber, LocalDate hireDate) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("orgId", orgId == null ? null : orgId.toString());
        payload.put("email", email);
        payload.put("firstName", firstName);
        payload.put("lastName", lastName);
        payload.put("employeeNumber", employeeNumber);
        payload.put("hireDate", hireDate == null ? null : hireDate.toString());
        try {
            kafkaTemplate.send(topic, userId, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize iam.user.created event for user {}", userId, e);
        }
    }
}
