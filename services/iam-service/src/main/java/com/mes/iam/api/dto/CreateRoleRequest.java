package com.mes.iam.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRoleRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,99}$",
                 message = "Role name must match ^[A-Z][A-Z0-9_]{0,99}$")
        String name,

        @Size(max = 500)
        String description
) {}
