package com.mes.engineering.workinstruction.kafka;

import com.mes.engineering.workinstruction.domain.WorkInstruction;
import com.mes.engineering.workinstruction.domain.WorkInstructionRevision;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes a domain event when a work-instruction revision is approved. Map payload with the
 * JSON value serializer (ERR-MES-063). Idempotent by (workInstructionId, revision); keyed by
 * work-instruction id so all events for one instruction land on the same partition.
 */
@Component
public class WorkInstructionEventPublisher {

    static final String TOPIC = "engineering.work-instruction.approved";

    private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    public WorkInstructionEventPublisher(KafkaTemplate<String, Map<String, Object>> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishApproved(WorkInstruction wi, WorkInstructionRevision revision) {
        Map<String, Object> event = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "WORK_INSTRUCTION_APPROVED",
                "workInstructionId", wi.getId().toString(),
                "identifier", wi.getIdentifier(),
                "orgId", wi.getOrgId().toString(),
                "revision", revision.getRevision(),
                "approvedBy", revision.getApprovedBy() != null ? revision.getApprovedBy() : "unknown",
                "occurredAt", Instant.now().toString()
        );
        kafkaTemplate.send(TOPIC, wi.getId().toString(), event);
    }
}
