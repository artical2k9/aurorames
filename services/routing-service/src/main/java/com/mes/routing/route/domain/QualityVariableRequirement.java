package com.mes.routing.route.domain;

import com.mes.routing.common.OrgAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

import java.util.UUID;

/** A quality variable (inspection-plan characteristic) collected at an operation (US3). */
@Entity
@Audited
@Table(name = "quality_variable_requirement", schema = "routing")
public class QualityVariableRequirement extends OrgAuditedEntity {

    @Column(name = "operation_id", nullable = false)
    private UUID operationId;

    @Column(name = "inspection_characteristic_id", nullable = false)
    private UUID inspectionCharacteristicId;

    public UUID getOperationId() {
        return operationId;
    }

    public void setOperationId(UUID operationId) {
        this.operationId = operationId;
    }

    public UUID getInspectionCharacteristicId() {
        return inspectionCharacteristicId;
    }

    public void setInspectionCharacteristicId(UUID inspectionCharacteristicId) {
        this.inspectionCharacteristicId = inspectionCharacteristicId;
    }
}
