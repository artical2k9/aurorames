package com.mes.routing.route.domain;

import com.mes.routing.common.OrgAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

import java.util.UUID;

/**
 * An ordered breakdown of a complex operation (US6). Normal/Parallel is derived from the
 * {@code stepSequenceNumber} within the operation (unique → Normal, shared → Parallel);
 * Optional is an explicit toggle. Mutually-exclusive step subsets use {@link MutuallyExclusiveSet}.
 */
@Entity
@Audited
@Table(name = "operation_step", schema = "routing")
public class OperationStep extends OrgAuditedEntity {

    @Column(name = "operation_id", nullable = false)
    private UUID operationId;

    @Column(name = "step_number", nullable = false)
    private int stepNumber;

    @Column(name = "step_sequence_number", nullable = false)
    private int stepSequenceNumber;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "optional", nullable = false)
    private boolean optional = false;

    public UUID getOperationId() {
        return operationId;
    }

    public void setOperationId(UUID operationId) {
        this.operationId = operationId;
    }

    public int getStepNumber() {
        return stepNumber;
    }

    public void setStepNumber(int stepNumber) {
        this.stepNumber = stepNumber;
    }

    public int getStepSequenceNumber() {
        return stepSequenceNumber;
    }

    public void setStepSequenceNumber(int stepSequenceNumber) {
        this.stepSequenceNumber = stepSequenceNumber;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isOptional() {
        return optional;
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
    }
}
