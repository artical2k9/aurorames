package com.mes.iam.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PrivilegeItemRequest(
        @NotBlank
        @Pattern(regexp = "^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$",
                 message = "Privilege key must match ^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$")
        String privilegeKey,

        @Size(max = 500)
        String description
) {}
