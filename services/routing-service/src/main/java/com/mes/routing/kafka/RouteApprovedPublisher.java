package com.mes.routing.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes {@link RouteApprovedEvent} as JSON to {@code routing.route.approved}. Serialises with
 * the shared {@link ObjectMapper} and sends via the String-valued {@link KafkaTemplate}, matching
 * the service's String serializer config (consistent with {@code SupersededEventListener}).
 */
@Component
public class RouteApprovedPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(RouteApprovedPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public RouteApprovedPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
            @Value("${routing.events.route-approved-topic:routing.route.approved}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    public void publish(RouteApprovedEvent event) {
        try {
            kafkaTemplate.send(topic, event.routeId().toString(), objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException ex) {
            // Serialisation of a simple record should never fail; log rather than break the approval.
            LOG.error("Failed to serialise RouteApprovedEvent for route {}", event.routeId(), ex);
        }
    }
}
