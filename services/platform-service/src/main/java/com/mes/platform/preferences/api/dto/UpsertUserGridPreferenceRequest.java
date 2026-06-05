package com.mes.platform.preferences.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpsertUserGridPreferenceRequest(
        @NotNull @Valid List<ColumnPreferenceEntry> columns
) {}
