package com.mes.inventory.kafka;

import com.mes.inventory.bom.domain.Bom;
import com.mes.inventory.bom.domain.BomRevision;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class BomEventPublisher {

    // Field names must exactly match BomReleasedEvent POJO in engineering-service (T063).
    static final String TOPIC = "bom.released";

    private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    public BomEventPublisher(KafkaTemplate<String, Map<String, Object>> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishApproved(Bom bom, BomRevision bomRevision) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "bom.released");
        event.put("bomId", bom.getId().toString());
        event.put("orgId", bom.getOrgId().toString());
        event.put("parentItemId", bom.getParentItemId().toString());
        event.put("revision", bomRevision.getRevision());
        // ecoId is nullable — plain UUID, no FK to engineering schema (§XI, Q1 UUID-only)
        event.put("ecoId", bomRevision.getEcoId() != null ? bomRevision.getEcoId().toString() : null);
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
