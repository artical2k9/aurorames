package com.mes.routing.route.domain;

import com.mes.routing.common.OrgAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.util.UUID;

/** A standard time on an operation: Labour Activity Type × Labour Plan Type × basis (FR-014). */
@Entity
@Audited
@Table(name = "labour_plan_line", schema = "routing")
public class LabourPlanLine extends OrgAuditedEntity {

    @Column(name = "operation_id", nullable = false)
    private UUID operationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "labour_activity_type", nullable = false, length = 20)
    private LabourActivityType labourActivityType;

    @Column(name = "labour_plan_type_id")
    private UUID labourPlanTypeId;

    @Column(name = "labour_code_id")
    private UUID labourCodeId;

    @Column(name = "time_value", nullable = false, precision = 12, scale = 4)
    private BigDecimal timeValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "basis", nullable = false, length = 10)
    private Basis basis;

    public UUID getOperationId() {
        return operationId;
    }

    public void setOperationId(UUID operationId) {
        this.operationId = operationId;
    }

    public LabourActivityType getLabourActivityType() {
        return labourActivityType;
    }

    public void setLabourActivityType(LabourActivityType labourActivityType) {
        this.labourActivityType = labourActivityType;
    }

    public UUID getLabourPlanTypeId() {
        return labourPlanTypeId;
    }

    public void setLabourPlanTypeId(UUID labourPlanTypeId) {
        this.labourPlanTypeId = labourPlanTypeId;
    }

    public UUID getLabourCodeId() {
        return labourCodeId;
    }

    public void setLabourCodeId(UUID labourCodeId) {
        this.labourCodeId = labourCodeId;
    }

    public BigDecimal getTimeValue() {
        return timeValue;
    }

    public void setTimeValue(BigDecimal timeValue) {
        this.timeValue = timeValue;
    }

    public Basis getBasis() {
        return basis;
    }

    public void setBasis(Basis basis) {
        this.basis = basis;
    }
}
