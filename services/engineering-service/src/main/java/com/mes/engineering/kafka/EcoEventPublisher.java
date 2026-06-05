package com.mes.engineering.kafka;

import com.mes.engineering.eco.domain.EngineeringChangeOrder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class EcoEventPublisher {

    static final String TOPIC = "engineering.eco.events";

    private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    public EcoEventPublisher(KafkaTemplate<String, Map<String, Object>> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishApproved(EngineeringChangeOrder eco) {
        publish("ECO_APPROVED", eco);
    }

    public void publishImplemented(EngineeringChangeOrder eco) {
        publish("ECO_IMPLEMENTED", eco);
    }

    private void publish(String eventType, EngineeringChangeOrder eco) {
        Map<String, Object> event = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", eventType,
                "entityId", eco.getId().toString(),
                "orgId", eco.getOrgId().toString(),
                "actorId", actorId(),
                "occurredAt", Instant.now().toString()
        );
        kafkaTemplate.send(TOPIC, eco.getId().toString(), event);
    }

    private String actorId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return "system";
        }
        var name = auth.getName();
        return name != null ? name : "system";
    }
}
