package com.mes.quality.inspectionplan.api.dto;

import jakarta.validation.constraints.Size;

import java.util.Map;

public class PatchInspectionPlanHeaderRequest {

    @Size(max = 200, message = "name must be at most 200 characters")
    private String name;

    private String description;

    @Size(max = 500, message = "reasonForRevision must be at most 500 characters")
    private String reasonForRevision;

    private Map<String, Object> customFields;

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

    public String getReasonForRevision() {
        return reasonForRevision;
    }

    public void setReasonForRevision(String reasonForRevision) {
        this.reasonForRevision = reasonForRevision;
    }

    public Map<String, Object> getCustomFields() {
        return customFields;
    }

    public void setCustomFields(Map<String, Object> customFields) {
        this.customFields = customFields;
    }
}
