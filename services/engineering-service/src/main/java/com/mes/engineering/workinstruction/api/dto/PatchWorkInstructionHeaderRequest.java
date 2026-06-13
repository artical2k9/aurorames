package com.mes.engineering.workinstruction.api.dto;

import jakarta.validation.constraints.Size;

import java.util.Map;

/** Partial update of a work-instruction revision header (auto-creates draft N+1 if current is APPROVED). */
public class PatchWorkInstructionHeaderRequest {

    @Size(max = 200)
    private String title;

    private String description;

    @Size(max = 100)
    private String partContext;

    @Size(max = 500)
    private String reasonForRevision;

    private Map<String, Object> customFields;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPartContext() {
        return partContext;
    }

    public void setPartContext(String partContext) {
        this.partContext = partContext;
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
