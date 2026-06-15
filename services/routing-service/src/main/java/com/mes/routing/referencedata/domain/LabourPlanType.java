package com.mes.routing.referencedata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

/** Extensible labour plan type (seeded Machine/People/OSP). An OSP plan type on an operation classifies it as OSP. */
@Entity
@Audited
@Table(name = "labour_plan_type", schema = "routing")
public class LabourPlanType extends AuditedReferenceEntity {

    @Column(name = "seeded", nullable = false)
    private boolean seeded = false;

    public boolean isSeeded() {
        return seeded;
    }

    public void setSeeded(boolean seeded) {
        this.seeded = seeded;
    }
}
