package com.mes.engineering.workinstruction.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Approve a pending revision. The approver re-enters their password; it is verified against
 * Keycloak (Direct Access Grant) and discarded — never stored. The meaning defaults to APPROVED.
 */
public class ApproveRevisionRequest {

    @NotBlank
    private String password;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
