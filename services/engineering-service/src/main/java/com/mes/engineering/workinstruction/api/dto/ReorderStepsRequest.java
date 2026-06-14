package com.mes.engineering.workinstruction.api.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/** Reorder steps: the ordered list of step ids defines the new step_number sequence (10, 20, 30, ...). */
public class ReorderStepsRequest {

    @NotEmpty
    private List<UUID> orderedStepIds;

    public List<UUID> getOrderedStepIds() {
        return orderedStepIds;
    }

    public void setOrderedStepIds(List<UUID> orderedStepIds) {
        this.orderedStepIds = orderedStepIds;
    }
}
