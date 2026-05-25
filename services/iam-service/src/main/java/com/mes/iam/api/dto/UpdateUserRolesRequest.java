package com.mes.iam.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateUserRolesRequest(
        @NotNull
        List<String> roles
) {}
