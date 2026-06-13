package com.mes.labour.certification.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public class EvaluateQualificationRequest {

    // Exactly one of employeeId / iamUserId must be provided (validated in service).
    private UUID employeeId;
    private String iamUserId;

    @NotNull
    private List<UUID> skillIds;

    public UUID getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(UUID employeeId) {
        this.employeeId = employeeId;
    }

    public String getIamUserId() {
        return iamUserId;
    }

    public void setIamUserId(String iamUserId) {
        this.iamUserId = iamUserId;
    }

    public List<UUID> getSkillIds() {
        return skillIds;
    }

    public void setSkillIds(List<UUID> skillIds) {
        this.skillIds = skillIds;
    }
}
