package com.mes.routing.route.domain;

import com.mes.routing.common.OrgAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

import java.util.UUID;

/** Gage/tooling consumed at an operation (US3). */
@Entity
@Audited
@Table(name = "tooling_requirement", schema = "routing")
public class ToolingRequirement extends OrgAuditedEntity {

    @Column(name = "operation_id", nullable = false)
    private UUID operationId;

    @Column(name = "gage_or_tool_ref", nullable = false, length = 100)
    private String gageOrToolRef;

    @Column(name = "description", length = 500)
    private String description;

    public UUID getOperationId() {
        return operationId;
    }

    public void setOperationId(UUID operationId) {
        this.operationId = operationId;
    }

    public String getGageOrToolRef() {
        return gageOrToolRef;
    }

    public void setGageOrToolRef(String gageOrToolRef) {
        this.gageOrToolRef = gageOrToolRef;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
