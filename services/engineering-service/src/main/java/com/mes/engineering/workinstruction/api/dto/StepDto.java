package com.mes.engineering.workinstruction.api.dto;

import java.util.Map;
import java.util.UUID;

public record StepDto(
        UUID id,
        Integer stepNumber,
        String title,
        String bodyHtml,
        Map<String, Object> customFields) {
}
