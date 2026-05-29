package com.mes.workorder.bom.api.dto;

import com.mes.workorder.bom.domain.EffectivityMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class CreateBomLineRequest {

    @NotNull
    private UUID componentItemId;

    @NotNull
    @Positive
    private BigDecimal quantity;

    @NotBlank
    private String unitOfMeasure;

    @NotBlank
    private String findNumber;

    private String referenceDesignators;
    private EffectivityMethod effectivityMethod;
    private LocalDate effectiveFromDate;
    private LocalDate effectiveToDate;
    private String effectiveFromUnit;
    private String effectiveToUnit;

    public UUID getComponentItemId() {
        return componentItemId;
    }

    public void setComponentItemId(UUID componentItemId) {
        this.componentItemId = componentItemId;
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
