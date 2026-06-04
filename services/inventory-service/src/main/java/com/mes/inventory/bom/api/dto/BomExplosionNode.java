package com.mes.inventory.bom.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;

public class BomExplosionNode {

    private String componentItemId;
    private String parentItemId;
    private int depth;
    private String findNumber;
    private BigDecimal quantity;
    private String makeBuyCode;
    private String partNumber;
    private String revision;
    private String description;
    private String unitOfMeasure;
    private boolean counterfeitRiskAlert;
    private boolean componentObsoleted;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<BomExplosionNode> children;

    public String getComponentItemId() {
        return componentItemId;
    }

    public void setComponentItemId(String componentItemId) {
        this.componentItemId = componentItemId;
    }

    public String getParentItemId() {
        return parentItemId;
    }

    public void setParentItemId(String parentItemId) {
        this.parentItemId = parentItemId;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
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

    public String getFindNumber() {
        return findNumber;
    }

    public void setFindNumber(String findNumber) {
        this.findNumber = findNumber;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getMakeBuyCode() {
        return makeBuyCode;
    }

    public void setMakeBuyCode(String makeBuyCode) {
        this.makeBuyCode = makeBuyCode;
    }

    public List<BomExplosionNode> getChildren() {
        return children;
    }

    public void setChildren(List<BomExplosionNode> children) {
        this.children = children;
    }
}
