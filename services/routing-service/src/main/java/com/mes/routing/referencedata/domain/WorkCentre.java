package com.mes.routing.referencedata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

/** A work centre / machine an operation is executed against (interim master, DEF-004). */
@Entity
@Audited
@Table(name = "work_centre", schema = "routing")
public class WorkCentre extends AuditedReferenceEntity {

    @Column(name = "description")
    private String description;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
