package com.mes.labour.certification.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class QualificationResultDto {

    private UUID employeeId;
    private boolean employeeActive;
    private List<SkillResult> results;

    public UUID getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(UUID employeeId) {
        this.employeeId = employeeId;
    }

    public boolean isEmployeeActive() {
        return employeeActive;
    }

    public void setEmployeeActive(boolean employeeActive) {
        this.employeeActive = employeeActive;
    }

    public List<SkillResult> getResults() {
        return results;
    }

    public void setResults(List<SkillResult> results) {
        this.results = results;
    }

    public static class SkillResult {

        private UUID skillId;
        private String skillCode;
        private String status;
        private LocalDate expiryDate;

        public UUID getSkillId() {
            return skillId;
        }

        public void setSkillId(UUID skillId) {
            this.skillId = skillId;
        }

        public String getSkillCode() {
            return skillCode;
        }

        public void setSkillCode(String skillCode) {
            this.skillCode = skillCode;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public LocalDate getExpiryDate() {
            return expiryDate;
        }

        public void setExpiryDate(LocalDate expiryDate) {
            this.expiryDate = expiryDate;
        }
    }
}
