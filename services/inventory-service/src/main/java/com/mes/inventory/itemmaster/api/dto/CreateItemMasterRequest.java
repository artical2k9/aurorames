package com.mes.inventory.itemmaster.api.dto;

import com.mes.inventory.itemmaster.domain.Classification;
import com.mes.inventory.itemmaster.domain.CounterfeitRiskLevel;
import com.mes.inventory.itemmaster.domain.MakeBuyCode;
import com.mes.inventory.itemmaster.domain.TraceabilityMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public class CreateItemMasterRequest {

    @NotBlank @Size(max = 100)
    private String partNumber;

    @NotBlank @Size(max = 20)
    private String revision;

    @NotBlank @Size(max = 500)
    private String description;

    @NotBlank @Size(max = 20)
    private String unitOfMeasure;

    @NotBlank @Size(max = 10)
    private String cageCode;

    @NotNull
    private Classification classification;

    @NotNull
    private MakeBuyCode makeBuyCode;

    @NotNull
    private TraceabilityMethod traceabilityMethod;

    private boolean shelfLifeControlled = false;

    private Integer shelfLifeDays;

    @Size(max = 255)
    private String stepPartRef;

    private CounterfeitRiskLevel counterfeitRiskLevel;

    private List<String> approvedSuppliers;

    private boolean verificationRequired = false;

    private Map<String, Object> customFields;

    // ── Getters and setters ───────────────────────────────────────────────────

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
}
