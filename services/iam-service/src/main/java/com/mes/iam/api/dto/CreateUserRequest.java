package com.mes.iam.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateUserRequest(
        @NotBlank @Email
        String email,

        @NotBlank @Size(max = 100)
        String firstName,

        @NotBlank @Size(max = 100)
        String lastName,

        @NotEmpty
        List<String> roles,

        // nullable — when absent a KC email invite is sent instead
        String initialPassword,

        // true = user must change on first login; ignored when initialPassword is absent
        boolean temporaryPassword
) {}
