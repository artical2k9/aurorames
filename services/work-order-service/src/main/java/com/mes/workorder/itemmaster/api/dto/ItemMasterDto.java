package com.mes.workorder.itemmaster.api.dto;

import com.mes.workorder.itemmaster.domain.Classification;
import com.mes.workorder.itemmaster.domain.CounterfeitRiskLevel;
import com.mes.workorder.itemmaster.domain.ItemStatus;
import com.mes.workorder.itemmaster.domain.MakeBuyCode;
import com.mes.workorder.itemmaster.domain.TraceabilityMethod;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ItemMasterDto {

    private UUID id;
    private UUID orgId;
    private String partNumber;
    private String revision;
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
    private ItemStatus status;
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
    public String getRevision() {
        return revision;
    }
    public void setRevision(String revision) {
        this.revision = revision;
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
    public ItemStatus getStatus() {
        return status;
    }
    public void setStatus(ItemStatus status) {
        this.status = status;
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
