package com.mes.workorder.eco.api.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public class EcoDto {

    private UUID id;
    private UUID orgId;
    private String ecoNumber;
    private String title;
    private String description;
    private String status;
    private String initiatedBy;
    private String approvedBy;
    private Instant approvedAt;
    private Set<UUID> affectedItemIds;
    private Set<UUID> outputBomIds;
    private boolean concurrentEcoWarning;
    private String createdBy;
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public void setOrgId(UUID orgId) {
        this.orgId = orgId;
    }

    public String getEcoNumber() {
        return ecoNumber;
    }

    public void setEcoNumber(String ecoNumber) {
        this.ecoNumber = ecoNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getInitiatedBy() {
        return initiatedBy;
    }

    public void setInitiatedBy(String initiatedBy) {
        this.initiatedBy = initiatedBy;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public Set<UUID> getAffectedItemIds() {
        return affectedItemIds;
    }

    public void setAffectedItemIds(Set<UUID> affectedItemIds) {
        this.affectedItemIds = affectedItemIds;
    }

    public Set<UUID> getOutputBomIds() {
        return outputBomIds;
    }

    public void setOutputBomIds(Set<UUID> outputBomIds) {
        this.outputBomIds = outputBomIds;
    }

    public boolean isConcurrentEcoWarning() {
        return concurrentEcoWarning;
    }

    public void setConcurrentEcoWarning(boolean concurrentEcoWarning) {
        this.concurrentEcoWarning = concurrentEcoWarning;
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
