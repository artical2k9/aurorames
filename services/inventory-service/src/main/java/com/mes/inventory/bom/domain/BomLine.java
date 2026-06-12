package com.mes.inventory.bom.domain;

import com.mes.inventory.itemmaster.domain.ItemRevision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Entity
@Audited
@EntityListeners(AuditingEntityListener.class)
@Table(name = "bom_line", schema = "inventory")
public class BomLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bom_revision_id", nullable = false)
    private BomRevision bomRevision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_item_revision_id", nullable = false)
    private ItemRevision componentItemRevision;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 6)
    private BigDecimal quantity;

    @Column(name = "unit_of_measure", nullable = false, length = 20)
    private String unitOfMeasure;

    @Column(name = "find_number", nullable = false, length = 20)
    private String findNumber;

    @Column(name = "reference_designators", length = 500)
    private String referenceDesignators;

    @Enumerated(EnumType.STRING)
    @Column(name = "effectivity_method", length = 10)
    private EffectivityMethod effectivityMethod;

    @Column(name = "effective_from_date")
    private LocalDate effectiveFromDate;

    @Column(name = "effective_to_date")
    private LocalDate effectiveToDate;

    @Column(name = "effective_from_unit", length = 50)
    private String effectiveFromUnit;

    @Column(name = "effective_to_unit", length = 50)
    private String effectiveToUnit;

    @Column(name = "custom_fields", columnDefinition = "jsonb")
    private Map<String, Object> customFields;

    @CreatedBy
    @Column(name = "created_by", nullable = false, length = 255, updatable = false)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedBy
    @Column(name = "modified_by", nullable = false, length = 255)
    private String modifiedBy;

    @LastModifiedDate
    @Column(name = "modified_at", nullable = false)
    private Instant modifiedAt;

    // ── Getters and setters ───────────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public BomRevision getBomRevision() {
        return bomRevision;
    }

    public void setBomRevision(BomRevision bomRevision) {
        this.bomRevision = bomRevision;
    }

    public ItemRevision getComponentItemRevision() {
        return componentItemRevision;
    }

    public void setComponentItemRevision(ItemRevision componentItemRevision) {
        this.componentItemRevision = componentItemRevision;
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

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getModifiedBy() {
        return modifiedBy;
    }

    public Instant getModifiedAt() {
        return modifiedAt;
    }

    public Map<String, Object> getCustomFields() {
        return customFields;
    }

    public void setCustomFields(Map<String, Object> customFields) {
        this.customFields = customFields;
    }
}
