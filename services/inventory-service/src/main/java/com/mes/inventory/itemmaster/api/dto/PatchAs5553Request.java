package com.mes.inventory.itemmaster.api.dto;

import com.mes.inventory.itemmaster.domain.CounterfeitRiskLevel;

import java.util.List;

public class PatchAs5553Request {

    private CounterfeitRiskLevel counterfeitRiskLevel;
    private List<String> approvedSuppliers;
    private Boolean verificationRequired;

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
    public Boolean getVerificationRequired() {
        return verificationRequired;
    }
    public void setVerificationRequired(Boolean verificationRequired) {
        this.verificationRequired = verificationRequired;
    }
}
