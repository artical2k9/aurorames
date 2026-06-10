package com.mes.inventory.bom.api.dto;

import java.time.Instant;
import java.util.UUID;

public class BomSummaryDto {

    private UUID bomId;
    private UUID bomRevisionId;
    private Integer revision;
    private String revisionStatus;
    private boolean hasDraft;
    private String description;
    private UUID parentItemId;
    private String partNumber;
    private Integer itemRevision;
    private String itemRevisionStatus;
    private String createdBy;
    private Instant createdAt;

    public UUID getBomId() {
        return bomId;
    }

    public void setBomId(UUID bomId) {
        this.bomId = bomId;
    }

    public UUID getBomRevisionId() {
        return bomRevisionId;
    }

    public void setBomRevisionId(UUID bomRevisionId) {
        this.bomRevisionId = bomRevisionId;
    }

    public Integer getRevision() {
        return revision;
    }

    public void setRevision(Integer revision) {
        this.revision = revision;
    }

    public String getRevisionStatus() {
        return revisionStatus;
    }

    public void setRevisionStatus(String revisionStatus) {
        this.revisionStatus = revisionStatus;
    }

    public boolean isHasDraft() {
        return hasDraft;
    }

    public void setHasDraft(boolean hasDraft) {
        this.hasDraft = hasDraft;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getParentItemId() {
        return parentItemId;
    }

    public void setParentItemId(UUID parentItemId) {
        this.parentItemId = parentItemId;
    }

    public String getPartNumber() {
        return partNumber;
    }

    public void setPartNumber(String partNumber) {
        this.partNumber = partNumber;
    }

    public Integer getItemRevision() {
        return itemRevision;
    }

    public void setItemRevision(Integer itemRevision) {
        this.itemRevision = itemRevision;
    }

    public String getItemRevisionStatus() {
        return itemRevisionStatus;
    }

    public void setItemRevisionStatus(String itemRevisionStatus) {
        this.itemRevisionStatus = itemRevisionStatus;
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
