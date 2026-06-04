package com.mes.inventory.itemmaster.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Type;
import org.hibernate.envers.Audited;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// ISA-95 Part 2 Material Class — a physical asset definition scoped to an organisation.
@Entity
@Audited
@EntityListeners(AuditingEntityListener.class)
@Table(name = "item_master", schema = "inventory")
public class ItemMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "part_number", nullable = false, length = 100)
    private String partNumber;

    @Column(name = "revision", nullable = false, length = 20)
    private String revision;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "unit_of_measure", nullable = false, length = 20)
    private String unitOfMeasure;

    @Column(name = "cage_code", length = 10)
    private String cageCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification", nullable = false, length = 30)
    private Classification classification;

    @Enumerated(EnumType.STRING)
    @Column(name = "make_buy_code", nullable = false, length = 10)
    private MakeBuyCode makeBuyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "traceability_method", nullable = false, length = 15)
    private TraceabilityMethod traceabilityMethod;

    @Column(name = "shelf_life_controlled", nullable = false)
    private boolean shelfLifeControlled = false;

    @Column(name = "shelf_life_days")
    private Integer shelfLifeDays;

    @Column(name = "step_part_ref", length = 255)
    private String stepPartRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "counterfeit_risk_level", length = 10)
    private CounterfeitRiskLevel counterfeitRiskLevel;

    @Type(JsonBinaryType.class)
    @Column(name = "approved_suppliers", columnDefinition = "jsonb")
    private List<String> approvedSuppliers;

    @Column(name = "verification_required", nullable = false)
    private boolean verificationRequired = false;

    @Type(JsonBinaryType.class)
    @Column(name = "custom_fields", columnDefinition = "jsonb")
    private Map<String, Object> customFields;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ItemStatus status = ItemStatus.ACTIVE;

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
    public String getModifiedBy() {
        return modifiedBy;
    }
    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }
    public Instant getModifiedAt() {
        return modifiedAt;
    }
}
