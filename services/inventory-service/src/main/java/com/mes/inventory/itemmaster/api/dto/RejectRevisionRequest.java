package com.mes.inventory.itemmaster.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RejectRevisionRequest {

    @NotBlank
    @Size(max = 500)
    private String rejectionReason;

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }
}
