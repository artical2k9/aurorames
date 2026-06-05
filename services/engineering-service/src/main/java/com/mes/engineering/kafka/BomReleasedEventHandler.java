package com.mes.engineering.kafka;

import com.mes.engineering.eco.service.EcoNotFoundException;
import com.mes.engineering.eco.service.EcoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

// engineering-service MAY have Kafka consumers — only inventory-service is producer-only per FR-013.
@Component
public class BomReleasedEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(BomReleasedEventHandler.class);

    private final EcoService ecoService;

    public BomReleasedEventHandler(EcoService ecoService) {
        this.ecoService = ecoService;
    }

    @KafkaListener(topics = "bom.released", groupId = "engineering-service")
    public void onBomReleased(@Payload BomReleasedEvent event) {
        if (event.getEcoId() == null) {
            return;
        }
        try {
            ecoService.addOutputBom(event.getEcoIdAsUuid(), event.getBomIdAsUuid());
        } catch (EcoNotFoundException ex) {
            LOG.warn("bom.released event references unknown ECO {}: {}", event.getEcoId(), ex.getMessage());
        }
    }
}
