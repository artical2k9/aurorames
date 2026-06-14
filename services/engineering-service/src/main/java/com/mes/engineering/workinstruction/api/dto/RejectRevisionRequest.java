package com.mes.engineering.workinstruction.api.dto;

import jakarta.validation.constraints.Size;

/**
 * Reject a pending revision; a reason is mandatory (auditability). Blankness is enforced in the
 * service (422 WorkInstructionValidationException) rather than via {@code @NotBlank} so the
 * response shape matches other business-rule failures.
 */
public class RejectRevisionRequest {

    @Size(max = 500)
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
