package com.mes.workorder.bom.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class BomDto {

    private UUID id;
    private UUID orgId;
    private UUID parentItemId;
    private String bomRevision;
    private String status;
    private String description;
    private UUID ecoId;
    private String createdBy;
    private Instant createdAt;
    private String reasonForRevision;
    private String productionLine;
    private String bomType;
    private String effectivityType;
    private Map<String, Object> customFields;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public void setOrgId(UUID orgId) {
        this.orgId = orgId;
    }

    public UUID getParentItemId() {
        return parentItemId;
    }

    public void setParentItemId(UUID parentItemId) {
        this.parentItemId = parentItemId;
    }

    public String getBomRevision() {
        return bomRevision;
    }

    public void setBomRevision(String bomRevision) {
        this.bomRevision = bomRevision;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getEcoId() {
        return ecoId;
    }

    public void setEcoId(UUID ecoId) {
        this.ecoId = ecoId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
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
