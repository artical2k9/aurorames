package com.mes.iam.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeTemporaryPasswordRequest(
        @NotBlank
        String username,

        @NotBlank
        String currentPassword,

        @NotBlank @Size(min = 8, max = 72)
        String newPassword
) {}
