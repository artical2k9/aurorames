package com.mes.routing.route.domain;

import com.mes.routing.common.OrgAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

import java.util.UUID;

/** The work instruction to follow at an operation (US3; references MES-10 work instructions). */
@Entity
@Audited
@Table(name = "work_instruction_link", schema = "routing")
public class WorkInstructionLink extends OrgAuditedEntity {

    @Column(name = "operation_id", nullable = false)
    private UUID operationId;

    @Column(name = "work_instruction_id", nullable = false)
    private UUID workInstructionId;

    public UUID getOperationId() {
        return operationId;
    }

    public void setOperationId(UUID operationId) {
        this.operationId = operationId;
    }

    public UUID getWorkInstructionId() {
        return workInstructionId;
    }

    public void setWorkInstructionId(UUID workInstructionId) {
        this.workInstructionId = workInstructionId;
    }
}
