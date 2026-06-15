package com.mes.routing.route.domain;

import com.mes.routing.common.OrgAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

import java.util.UUID;

/** A BOM line consumed at an operation, with a mandatory flag (US3). */
@Entity
@Audited
@Table(name = "material_consumption", schema = "routing")
public class MaterialConsumption extends OrgAuditedEntity {

    @Column(name = "operation_id", nullable = false)
    private UUID operationId;

    @Column(name = "bom_line_id", nullable = false)
    private UUID bomLineId;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory = false;

    public UUID getOperationId() {
        return operationId;
    }

    public void setOperationId(UUID operationId) {
        this.operationId = operationId;
    }

    public UUID getBomLineId() {
        return bomLineId;
    }

    public void setBomLineId(UUID bomLineId) {
        this.bomLineId = bomLineId;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }
}
