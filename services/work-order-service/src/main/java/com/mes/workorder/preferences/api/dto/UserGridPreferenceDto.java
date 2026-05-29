package com.mes.workorder.preferences.api.dto;

import java.util.List;

public record UserGridPreferenceDto(String moduleKey, List<ColumnPreferenceEntry> columns) {}
