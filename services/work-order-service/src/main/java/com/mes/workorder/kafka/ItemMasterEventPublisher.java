package com.mes.workorder.kafka;

import com.mes.workorder.itemmaster.api.dto.ItemMasterDto;
import com.mes.workorder.itemmaster.api.dto.ItemMasterMapper;
import com.mes.workorder.itemmaster.domain.ItemMaster;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class ItemMasterEventPublisher {

    static final String TOPIC = "work-order.item-master.events";

    private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    public ItemMasterEventPublisher(KafkaTemplate<String, Map<String, Object>> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishCreated(ItemMaster entity) {
        publish("ITEM_MASTER_CREATED", entity);
    }

    public void publishUpdated(ItemMaster entity) {
        publish("ITEM_MASTER_UPDATED", entity);
    }

    private void publish(String eventType, ItemMaster entity) {
        ItemMasterDto dto = ItemMasterMapper.toDto(entity);
        Map<String, Object> event = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", eventType,
                "entityId", entity.getId().toString(),
                "orgId", entity.getOrgId().toString(),
                "actorId", actorId(),
                "occurredAt", Instant.now().toString(),
                "payload", dto
        );
        kafkaTemplate.send(TOPIC, entity.getId().toString(), event);
    }

    private String actorId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
