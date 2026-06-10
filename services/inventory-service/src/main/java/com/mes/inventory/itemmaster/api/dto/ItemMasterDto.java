package com.mes.inventory.itemmaster.api.dto;

import com.mes.inventory.itemmaster.domain.Classification;
import com.mes.inventory.itemmaster.domain.CounterfeitRiskLevel;
import com.mes.inventory.itemmaster.domain.MakeBuyCode;
import com.mes.inventory.itemmaster.domain.RevisionStatus;
import com.mes.inventory.itemmaster.domain.TraceabilityMethod;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ItemMasterDto {

    // identity fields
    private UUID id;
    private UUID orgId;
    private String partNumber;

    // revision fields
    private UUID revisionId;
    private Integer revision;
    private RevisionStatus revisionStatus;
    private boolean hasDraft;

    // data fields
    private String description;
    private String unitOfMeasure;
    private String cageCode;
    private Classification classification;
    private MakeBuyCode makeBuyCode;
    private TraceabilityMethod traceabilityMethod;
    private boolean shelfLifeControlled;
    private Integer shelfLifeDays;
    private String stepPartRef;
    private CounterfeitRiskLevel counterfeitRiskLevel;
    private List<String> approvedSuppliers;
    private boolean verificationRequired;
    private Map<String, Object> customFields;

    // workflow audit fields
    private String submittedBy;
    private Instant submittedAt;
    private String approvedBy;
    private Instant approvedAt;
    private String rejectedBy;
    private Instant rejectedAt;
    private String rejectionReason;

    // system audit fields
    private String createdBy;
    private Instant createdAt;
    private String modifiedBy;
    private Instant modifiedAt;

    // ── Getters and setters ───────────────────────────────────────────────────

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

    public String getPartNumber() {
        return partNumber;
    }

    public void setPartNumber(String partNumber) {
        this.partNumber = partNumber;
    }

    public UUID getRevisionId() {
        return revisionId;
    }

    public void setRevisionId(UUID revisionId) {
        this.revisionId = revisionId;
    }

    public Integer getRevision() {
        return revision;
    }

    public void setRevision(Integer revision) {
        this.revision = revision;
    }

    public RevisionStatus getRevisionStatus() {
        return revisionStatus;
    }

    public void setRevisionStatus(RevisionStatus revisionStatus) {
        this.revisionStatus = revisionStatus;
    }

    public boolean isHasDraft() {
        return hasDraft;
    }

    public void setHasDraft(boolean hasDraft) {
        this.hasDraft = hasDraft;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUnitOfMeasure() {
        return unitOfMeasure;
    }

    public void setUnitOfMeasure(String unitOfMeasure) {
        this.unitOfMeasure = unitOfMeasure;
    }

    public String getCageCode() {
        return cageCode;
    }

    public void setCageCode(String cageCode) {
        this.cageCode = cageCode;
    }

    public Classification getClassification() {
        return classification;
    }

    public void setClassification(Classification classification) {
        this.classification = classification;
    }

    public MakeBuyCode getMakeBuyCode() {
        return makeBuyCode;
    }

    public void setMakeBuyCode(MakeBuyCode makeBuyCode) {
        this.makeBuyCode = makeBuyCode;
    }

    public TraceabilityMethod getTraceabilityMethod() {
        return traceabilityMethod;
    }

    public void setTraceabilityMethod(TraceabilityMethod traceabilityMethod) {
        this.traceabilityMethod = traceabilityMethod;
    }

    public boolean isShelfLifeControlled() {
        return shelfLifeControlled;
    }

    public void setShelfLifeControlled(boolean shelfLifeControlled) {
        this.shelfLifeControlled = shelfLifeControlled;
    }

    public Integer getShelfLifeDays() {
        return shelfLifeDays;
    }

    public void setShelfLifeDays(Integer shelfLifeDays) {
        this.shelfLifeDays = shelfLifeDays;
    }

    public String getStepPartRef() {
        return stepPartRef;
    }

    public void setStepPartRef(String stepPartRef) {
        this.stepPartRef = stepPartRef;
    }

    public CounterfeitRiskLevel getCounterfeitRiskLevel() {
        return counterfeitRiskLevel;
    }

    public void setCounterfeitRiskLevel(CounterfeitRiskLevel counterfeitRiskLevel) {
        this.counterfeitRiskLevel = counterfeitRiskLevel;
    }

    public List<String> getApprovedSuppliers() {
        return approvedSuppliers;
    }

    public void setApprovedSuppliers(List<String> approvedSuppliers) {
        this.approvedSuppliers = approvedSuppliers;
    }

    public boolean isVerificationRequired() {
        return verificationRequired;
    }

    public void setVerificationRequired(boolean verificationRequired) {
        this.verificationRequired = verificationRequired;
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
