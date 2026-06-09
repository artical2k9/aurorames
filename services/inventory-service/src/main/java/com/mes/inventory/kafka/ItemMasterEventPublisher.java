package com.mes.inventory.kafka;

import com.mes.inventory.itemmaster.domain.ItemRevision;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class ItemMasterEventPublisher {

    static final String TOPIC = "work-order.item-master.events";

    private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    public ItemMasterEventPublisher(KafkaTemplate<String, Map<String, Object>> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishAs5553RiskAdded(ItemRevision component) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", "compliance.as5553-risk-added");
        event.put("entityId", component.getId().toString());
        event.put("orgId", component.getItem().getOrgId().toString());
        event.put("partNumber", component.getItem().getPartNumber());
        event.put("counterfeitRiskLevel", component.getCounterfeitRiskLevel().name());
        event.put("actorId", actorId());
        event.put("occurredAt", Instant.now().toString());
        kafkaTemplate.send(TOPIC, component.getId().toString(), event);
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
