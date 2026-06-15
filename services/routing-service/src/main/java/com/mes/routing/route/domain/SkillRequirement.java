package com.mes.routing.route.domain;

import com.mes.routing.common.OrgAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

import java.util.UUID;

/** A labour skill required to execute an operation (US3; references MES-11 skills). */
@Entity
@Audited
@Table(name = "skill_requirement", schema = "routing")
public class SkillRequirement extends OrgAuditedEntity {

    @Column(name = "operation_id", nullable = false)
    private UUID operationId;

    @Column(name = "skill_id", nullable = false)
    private UUID skillId;

    public UUID getOperationId() {
        return operationId;
    }

    public void setOperationId(UUID operationId) {
        this.operationId = operationId;
    }

    public UUID getSkillId() {
        return skillId;
    }

    public void setSkillId(UUID skillId) {
        this.skillId = skillId;
    }
}
