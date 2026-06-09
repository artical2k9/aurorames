package com.mes.inventory.bom.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class BomLineDto {

    private UUID id;
    private UUID bomRevisionId;
    private UUID componentItemRevisionId;
    private Integer componentRevision;
    private BigDecimal quantity;
    private String unitOfMeasure;
    private String findNumber;
    private String referenceDesignators;
    private String effectivityMethod;
    private LocalDate effectiveFromDate;
    private LocalDate effectiveToDate;
    private String effectiveFromUnit;
    private String effectiveToUnit;
    private boolean counterfeitRiskAlert;
    private boolean componentObsoleted;
    private String partNumber;
    private String description;
    private String makeBuyCode;
    private String createdBy;
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getBomRevisionId() {
        return bomRevisionId;
    }

    public void setBomRevisionId(UUID bomRevisionId) {
        this.bomRevisionId = bomRevisionId;
    }

    public UUID getComponentItemRevisionId() {
        return componentItemRevisionId;
    }

    public void setComponentItemRevisionId(UUID componentItemRevisionId) {
        this.componentItemRevisionId = componentItemRevisionId;
    }

    public Integer getComponentRevision() {
        return componentRevision;
    }

    public void setComponentRevision(Integer componentRevision) {
        this.componentRevision = componentRevision;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getUnitOfMeasure() {
        return unitOfMeasure;
    }

    public void setUnitOfMeasure(String unitOfMeasure) {
        this.unitOfMeasure = unitOfMeasure;
    }

    public String getFindNumber() {
        return findNumber;
    }

    public void setFindNumber(String findNumber) {
        this.findNumber = findNumber;
    }

    public String getReferenceDesignators() {
        return referenceDesignators;
    }

    public void setReferenceDesignators(String referenceDesignators) {
        this.referenceDesignators = referenceDesignators;
    }

    public String getEffectivityMethod() {
        return effectivityMethod;
    }

    public void setEffectivityMethod(String effectivityMethod) {
        this.effectivityMethod = effectivityMethod;
    }

    public LocalDate getEffectiveFromDate() {
        return effectiveFromDate;
    }

    public void setEffectiveFromDate(LocalDate effectiveFromDate) {
        this.effectiveFromDate = effectiveFromDate;
    }

    public LocalDate getEffectiveToDate() {
        return effectiveToDate;
    }

    public void setEffectiveToDate(LocalDate effectiveToDate) {
        this.effectiveToDate = effectiveToDate;
    }

    public String getEffectiveFromUnit() {
        return effectiveFromUnit;
    }

    public void setEffectiveFromUnit(String effectiveFromUnit) {
        this.effectiveFromUnit = effectiveFromUnit;
    }

    public String getEffectiveToUnit() {
        return effectiveToUnit;
    }

    public void setEffectiveToUnit(String effectiveToUnit) {
        this.effectiveToUnit = effectiveToUnit;
    }

    public boolean isCounterfeitRiskAlert() {
        return counterfeitRiskAlert;
    }

    public void setCounterfeitRiskAlert(boolean counterfeitRiskAlert) {
        this.counterfeitRiskAlert = counterfeitRiskAlert;
    }

    public boolean isComponentObsoleted() {
        return componentObsoleted;
    }

    public void setComponentObsoleted(boolean componentObsoleted) {
        this.componentObsoleted = componentObsoleted;
    }

    public String getPartNumber() {
        return partNumber;
    }

    public void setPartNumber(String partNumber) {
        this.partNumber = partNumber;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMakeBuyCode() {
        return makeBuyCode;
    }

    public void setMakeBuyCode(String makeBuyCode) {
        this.makeBuyCode = makeBuyCode;
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
}
