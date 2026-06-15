package com.mes.routing.referencedata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

import java.util.UUID;

/** A labour code referenced by an operation's labour plan; optionally linked to a labour plan type. */
@Entity
@Audited
@Table(name = "labour_code", schema = "routing")
public class LabourCode extends AuditedReferenceEntity {

    @Column(name = "labour_plan_type_id")
    private UUID labourPlanTypeId;

    public UUID getLabourPlanTypeId() {
        return labourPlanTypeId;
    }

    public void setLabourPlanTypeId(UUID labourPlanTypeId) {
        this.labourPlanTypeId = labourPlanTypeId;
    }
}
