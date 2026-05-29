package com.mes.workorder.bom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Audited
@EntityListeners(AuditingEntityListener.class)
@Table(name = "bill_of_materials", schema = "work_order")
public class BillOfMaterials {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "parent_item_id", nullable = false)
    private UUID parentItemId;

    @Column(name = "bom_revision", nullable = false, length = 20)
    private String bomRevision;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private BomStatus status = BomStatus.DRAFT;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "eco_id")
    private UUID ecoId;

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

    public UUID getParentItemId() {
        return parentItemId;
    }

    public void setParentItemId(UUID parentItemId) {
        this.parentItemId = parentItemId;
    }

    public String getBomRevision() {
        return bomRevision;
    }

    public void setBomRevision(String bomRevision) {
        this.bomRevision = bomRevision;
    }

    public BomStatus getStatus() {
        return status;
    }

    public void setStatus(BomStatus status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getEcoId() {
        return ecoId;
    }

    public void setEcoId(UUID ecoId) {
        this.ecoId = ecoId;
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
}
