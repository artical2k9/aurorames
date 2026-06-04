package com.mes.inventory.kafka;

import com.mes.inventory.bom.domain.BillOfMaterials;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class BomEventPublisher {

    // Field names must exactly match BomReleasedEvent POJO in engineering-service (T063).
    static final String TOPIC = "bom.released";

    private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    public BomEventPublisher(KafkaTemplate<String, Map<String, Object>> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishReleased(BillOfMaterials bom) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "bom.released");
        event.put("bomId", bom.getId().toString());
        event.put("orgId", bom.getOrgId().toString());
        event.put("parentItemId", bom.getParentItemId().toString());
        event.put("bomRevision", bom.getBomRevision());
        // ecoId is nullable — plain UUID, no FK to engineering schema (§XI, Q1 UUID-only)
        event.put("ecoId", bom.getEcoId() != null ? bom.getEcoId().toString() : null);
        event.put("actorId", actorId());
        event.put("occurredAt", Instant.now().toString());
        kafkaTemplate.send(TOPIC, bom.getId().toString(), event);
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
