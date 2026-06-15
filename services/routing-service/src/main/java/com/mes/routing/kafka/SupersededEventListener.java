package com.mes.routing.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mes.routing.route.service.RouteSupersedeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes upstream BOM / inspection-plan revision-superseded events and flags affected routes
 * (FR-004f). Malformed payloads are logged and skipped so a poison message does not stall the
 * partition; transient infra errors propagate for retry. Idempotent (§VIII).
 */
@Component
public class SupersededEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(SupersededEventListener.class);

    private final RouteSupersedeService supersedeService;
    private final ObjectMapper objectMapper;

    public SupersededEventListener(RouteSupersedeService supersedeService, ObjectMapper objectMapper) {
        this.supersedeService = supersedeService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${routing.events.bom-revision-superseded-topic:inventory.bom.revision.superseded}",
            groupId = "${spring.kafka.consumer.group-id:routing-service}")
    public void onBomRevisionSuperseded(String message) {
        UUID revisionId = parse(message);
        if (revisionId != null) {
            supersedeService.markBomRevisionSuperseded(revisionId);
        }
    }

    @KafkaListener(
            topics = "${routing.events.inspection-plan-revision-superseded-topic:"
                    + "quality.inspection-plan.revision.superseded}",
            groupId = "${spring.kafka.consumer.group-id:routing-service}")
    public void onInspectionPlanRevisionSuperseded(String message) {
        UUID revisionId = parse(message);
        if (revisionId != null) {
            supersedeService.markInspectionPlanRevisionSuperseded(revisionId);
        }
    }

    private UUID parse(String message) {
        try {
            RevisionSupersededEvent event = objectMapper.readValue(message, RevisionSupersededEvent.class);
            if (event.revisionId() == null || event.revisionId().isBlank()) {
                LOG.warn("revision.superseded event missing revisionId — skipping: {}", message);
                return null;
            }
            return UUID.fromString(event.revisionId());
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            LOG.error("Skipping malformed revision.superseded event: {}", message, ex);
            return null;
        }
    }
}
