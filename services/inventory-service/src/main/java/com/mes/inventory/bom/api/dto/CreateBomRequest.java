package com.mes.inventory.bom.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class CreateBomRequest {

    @NotNull
    private UUID parentItemId;

    private String description;

    private UUID ecoId;

    public UUID getParentItemId() {
        return parentItemId;
    }

    public void setParentItemId(UUID parentItemId) {
        this.parentItemId = parentItemId;
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
}
