package com.mes.iam.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RegisterPrivilegesRequest(
        @NotBlank
        String moduleName,

        @NotEmpty
        @Valid
        List<PrivilegeItemRequest> privileges
) {}
