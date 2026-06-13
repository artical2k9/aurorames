package com.mes.quality.kafka;

import com.mes.quality.inspectionplan.domain.InspectionPlan;
import com.mes.quality.inspectionplan.domain.InspectionPlanRevision;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Publishes the approval event consumed by MES-9 (route creation / work-order release gating).
 * JsonSerializer payload (ERR-MES-063); idempotency key = (planId, revision).
 */
@Component
public class QualityEventPublisher {

    static final String TOPIC = "quality.inspection-plan.approved";

    private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    public QualityEventPublisher(KafkaTemplate<String, Map<String, Object>> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishApproved(InspectionPlan plan, InspectionPlanRevision revision) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "quality.inspection-plan.approved");
        event.put("orgId", plan.getOrgId().toString());
        event.put("planId", plan.getId().toString());
        event.put("itemId", plan.getItemId().toString());
        event.put("partNumber", plan.getPartNumber());
        event.put("revision", revision.getRevision());
        event.put("approvedBy", revision.getApprovedBy());
        event.put("approvedAt", revision.getApprovedAt() != null
                ? revision.getApprovedAt().toString() : Instant.now().toString());
        kafkaTemplate.send(TOPIC, plan.getId().toString(), event);
    }
}
