package com.mes.routing.referencedata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

/** Route classification; seeded protected Standard (one per org), alternates (NPI/FAI/...) user-added. */
@Entity
@Audited
@Table(name = "route_type", schema = "routing")
public class RouteType extends AuditedReferenceEntity {

    @Column(name = "is_standard", nullable = false)
    private boolean isStandard = false;

    @Column(name = "seeded", nullable = false)
    private boolean seeded = false;

    public boolean isStandard() {
        return isStandard;
    }

    public void setStandard(boolean standard) {
        this.isStandard = standard;
    }

    public boolean isSeeded() {
        return seeded;
    }

    public void setSeeded(boolean seeded) {
        this.seeded = seeded;
    }
}
