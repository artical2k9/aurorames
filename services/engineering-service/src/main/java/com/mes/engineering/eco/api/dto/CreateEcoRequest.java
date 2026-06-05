package com.mes.engineering.eco.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public class CreateEcoRequest {

    @NotBlank
    private String title;

    private String description;

    @NotEmpty
    private List<UUID> affectedItemIds;

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

    public List<UUID> getAffectedItemIds() {
        return affectedItemIds;
    }

    public void setAffectedItemIds(List<UUID> affectedItemIds) {
        this.affectedItemIds = affectedItemIds;
    }
}
