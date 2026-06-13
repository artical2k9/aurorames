package com.mes.labour.skill.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Map;

public class CreateSkillRequest {

    @NotBlank
    @Size(max = 50)
    private String skillCode;

    @NotBlank
    @Size(max = 200)
    private String name;

    private String description;

    @Size(max = 100)
    private String category;

    private Boolean certificationRequired;

    @Positive
    private Integer validityMonths;

    private Map<String, Object> customFields;

    public String getSkillCode() {
        return skillCode;
    }

    public void setSkillCode(String skillCode) {
        this.skillCode = skillCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Boolean getCertificationRequired() {
        return certificationRequired;
    }

    public void setCertificationRequired(Boolean certificationRequired) {
        this.certificationRequired = certificationRequired;
    }

    public Integer getValidityMonths() {
        return validityMonths;
    }

    public void setValidityMonths(Integer validityMonths) {
        this.validityMonths = validityMonths;
    }

    public Map<String, Object> getCustomFields() {
        return customFields;
    }

    public void setCustomFields(Map<String, Object> customFields) {
        this.customFields = customFields;
    }
}
