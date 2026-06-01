package com.mes.workorder.bom.api.dto;

import jakarta.validation.constraints.Size;

import java.util.Map;

public class PatchBomHeaderRequest {

    @Size(max = 500)
    private String description;

    @Size(max = 500)
    private String reasonForRevision;

    @Size(max = 200)
    private String productionLine;

    @Size(max = 30)
    private String bomType;

    @Size(max = 10)
    private String effectivityType;

    private Map<String, Object> customFields;

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

    public String getProductionLine() {
        return productionLine;
    }

    public void setProductionLine(String productionLine) {
        this.productionLine = productionLine;
    }

    public String getBomType() {
        return bomType;
    }

    public void setBomType(String bomType) {
        this.bomType = bomType;
    }

    public String getEffectivityType() {
        return effectivityType;
    }

    public void setEffectivityType(String effectivityType) {
        this.effectivityType = effectivityType;
    }

    public Map<String, Object> getCustomFields() {
        return customFields;
    }

    public void setCustomFields(Map<String, Object> customFields) {
        this.customFields = customFields;
    }
}
