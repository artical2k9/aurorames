package com.mes.routing.referencedata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

/** A special-process type (e.g. brazing, EB welding) with the approver role required to approve it (FR-024). */
@Entity
@Audited
@Table(name = "significant_process_type", schema = "routing")
public class SignificantProcessType extends AuditedReferenceEntity {

    @Column(name = "required_approver_role", nullable = false, length = 100)
    private String requiredApproverRole;

    public String getRequiredApproverRole() {
        return requiredApproverRole;
    }

    public void setRequiredApproverRole(String requiredApproverRole) {
        this.requiredApproverRole = requiredApproverRole;
    }
}
