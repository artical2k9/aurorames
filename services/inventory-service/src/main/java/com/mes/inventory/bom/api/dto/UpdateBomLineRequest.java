package com.mes.inventory.bom.api.dto;

import com.mes.inventory.bom.domain.EffectivityMethod;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public class UpdateBomLineRequest {

    @Positive
    private BigDecimal quantity;
    private String unitOfMeasure;
    private String findNumber;
    private String referenceDesignators;
    private EffectivityMethod effectivityMethod;
    private LocalDate effectiveFromDate;
    private LocalDate effectiveToDate;
    private String effectiveFromUnit;
    private String effectiveToUnit;

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

    public EffectivityMethod getEffectivityMethod() {
        return effectivityMethod;
    }

    public void setEffectivityMethod(EffectivityMethod effectivityMethod) {
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
}
