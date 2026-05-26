package com.mes.workorder.itemmaster.api.dto;

import com.mes.workorder.itemmaster.domain.CounterfeitRiskLevel;
import com.mes.workorder.itemmaster.domain.MakeBuyCode;
import com.mes.workorder.itemmaster.domain.TraceabilityMethod;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public class PatchItemMasterRequest {

    @Size(max = 500)
    private String description;

    @Size(max = 20)
    private String unitOfMeasure;

    @Size(max = 10)
    private String cageCode;

    private MakeBuyCode makeBuyCode;
    private TraceabilityMethod traceabilityMethod;
    private Boolean shelfLifeControlled;
    private Integer shelfLifeDays;

    @Size(max = 255)
    private String stepPartRef;

    private CounterfeitRiskLevel counterfeitRiskLevel;
    private List<String> approvedSuppliers;
    private Boolean verificationRequired;
    private Map<String, Object> customFields;

    // ── Getters and setters ───────────────────────────────────────────────────

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getUnitOfMeasure() { return unitOfMeasure; }
    public void setUnitOfMeasure(String unitOfMeasure) { this.unitOfMeasure = unitOfMeasure; }
    public String getCageCode() { return cageCode; }
    public void setCageCode(String cageCode) { this.cageCode = cageCode; }
    public MakeBuyCode getMakeBuyCode() { return makeBuyCode; }
    public void setMakeBuyCode(MakeBuyCode makeBuyCode) { this.makeBuyCode = makeBuyCode; }
    public TraceabilityMethod getTraceabilityMethod() { return traceabilityMethod; }
    public void setTraceabilityMethod(TraceabilityMethod traceabilityMethod) { this.traceabilityMethod = traceabilityMethod; }
    public Boolean getShelfLifeControlled() { return shelfLifeControlled; }
    public void setShelfLifeControlled(Boolean shelfLifeControlled) { this.shelfLifeControlled = shelfLifeControlled; }
    public Integer getShelfLifeDays() { return shelfLifeDays; }
    public void setShelfLifeDays(Integer shelfLifeDays) { this.shelfLifeDays = shelfLifeDays; }
    public String getStepPartRef() { return stepPartRef; }
    public void setStepPartRef(String stepPartRef) { this.stepPartRef = stepPartRef; }
    public CounterfeitRiskLevel getCounterfeitRiskLevel() { return counterfeitRiskLevel; }
    public void setCounterfeitRiskLevel(CounterfeitRiskLevel counterfeitRiskLevel) { this.counterfeitRiskLevel = counterfeitRiskLevel; }
    public List<String> getApprovedSuppliers() { return approvedSuppliers; }
    public void setApprovedSuppliers(List<String> approvedSuppliers) { this.approvedSuppliers = approvedSuppliers; }
    public Boolean getVerificationRequired() { return verificationRequired; }
    public void setVerificationRequired(Boolean verificationRequired) { this.verificationRequired = verificationRequired; }
    public Map<String, Object> getCustomFields() { return customFields; }
    public void setCustomFields(Map<String, Object> customFields) { this.customFields = customFields; }
}
