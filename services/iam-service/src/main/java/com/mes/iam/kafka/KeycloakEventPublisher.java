package com.mes.iam.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class KeycloakEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakEventPublisher.class);

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final String topic;
    private final ObjectMapper objectMapper;

    public KeycloakEventPublisher(
            KafkaTemplate<Object, Object> kafkaTemplate,
            @Value("${iam.events.topic:iam.events}") String topic,
            ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.objectMapper = objectMapper;
    }

    public void publishEvent(String eventType, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, eventType, json);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize Keycloak event — event type: {}", eventType, e);
        }
    }
}
