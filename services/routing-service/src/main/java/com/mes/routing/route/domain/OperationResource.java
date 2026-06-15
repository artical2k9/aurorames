package com.mes.routing.route.domain;

import com.mes.routing.common.OrgAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

import java.util.UUID;

/** An eligible work centre/machine for an operation (US3). */
@Entity
@Audited
@Table(name = "operation_resource", schema = "routing")
public class OperationResource extends OrgAuditedEntity {

    @Column(name = "operation_id", nullable = false)
    private UUID operationId;

    @Column(name = "work_centre_id", nullable = false)
    private UUID workCentreId;

    public UUID getOperationId() {
        return operationId;
    }

    public void setOperationId(UUID operationId) {
        this.operationId = operationId;
    }

    public UUID getWorkCentreId() {
        return workCentreId;
    }

    public void setWorkCentreId(UUID workCentreId) {
        this.workCentreId = workCentreId;
    }
}
