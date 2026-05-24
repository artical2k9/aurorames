package com.mikemes.platform.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpsertConfigRequest(
        @NotNull String value,
        @Size(max = 1000) String description
) {}
