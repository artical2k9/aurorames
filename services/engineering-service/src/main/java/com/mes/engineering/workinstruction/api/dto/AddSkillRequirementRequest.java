package com.mes.engineering.workinstruction.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Add a skill requirement to the editable draft. Code/name are denormalised from labour-service. */
public class AddSkillRequirementRequest {

    @NotNull
    private UUID skillId;

    public UUID getSkillId() {
        return skillId;
    }

    public void setSkillId(UUID skillId) {
        this.skillId = skillId;
    }
}
