package com.mes.inventory.bom.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class BomDto {

    private UUID id;
    private UUID orgId;
    private UUID parentItemId;
    private UUID bomRevisionId;
    private Integer revision;
    private String revisionStatus;
    private boolean hasDraft;
    private boolean hasPendingApproval;
    private String description;
    private UUID ecoId;
    private String reasonForRevision;
    private String productionLine;
    private String bomType;
    private String effectivityType;
    private Map<String, Object> customFields;
    private String submittedBy;
    private Instant submittedAt;
    private String approvedBy;
    private Instant approvedAt;
    private String rejectedBy;
    private Instant rejectedAt;
    private String rejectionReason;
    private String createdBy;
    private Instant createdAt;
    private String modifiedBy;
    private Instant modifiedAt;

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

    public UUID getBomRevisionId() {
        return bomRevisionId;
    }

    public void setBomRevisionId(UUID bomRevisionId) {
        this.bomRevisionId = bomRevisionId;
    }

    public Integer getRevision() {
        return revision;
    }

    public void setRevision(Integer revision) {
        this.revision = revision;
    }

    public String getRevisionStatus() {
        return revisionStatus;
    }

    public void setRevisionStatus(String revisionStatus) {
        this.revisionStatus = revisionStatus;
    }

    public boolean isHasDraft() {
        return hasDraft;
    }

    public void setHasDraft(boolean hasDraft) {
        this.hasDraft = hasDraft;
    }

    public boolean isHasPendingApproval() {
        return hasPendingApproval;
    }

    public void setHasPendingApproval(boolean hasPendingApproval) {
        this.hasPendingApproval = hasPendingApproval;
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

    public String getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(String submittedBy) {
        this.submittedBy = submittedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getRejectedBy() {
        return rejectedBy;
    }

    public void setRejectedBy(String rejectedBy) {
        this.rejectedBy = rejectedBy;
    }

    public Instant getRejectedAt() {
        return rejectedAt;
    }

    public void setRejectedAt(Instant rejectedAt) {
        this.rejectedAt = rejectedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
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

    public String getModifiedBy() {
        return modifiedBy;
    }

    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    public Instant getModifiedAt() {
        return modifiedAt;
    }

    public void setModifiedAt(Instant modifiedAt) {
        this.modifiedAt = modifiedAt;
    }
}
