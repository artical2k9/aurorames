package com.mes.engineering.workinstruction.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** Request to create a new work instruction with its initial DRAFT revision 0. */
public class CreateWorkInstructionRequest {

    @NotBlank
    @Size(max = 40)
    private String identifier;

    @NotBlank
    @Size(max = 200)
    private String title;

    private String description;

    @Size(max = 100)
    private String partContext;

    private Map<String, Object> customFields;

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

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

    public Map<String, Object> getCustomFields() {
        return customFields;
    }

    public void setCustomFields(Map<String, Object> customFields) {
        this.customFields = customFields;
    }
}
