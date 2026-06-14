package com.mes.engineering.workinstruction.api.dto;

import java.util.Map;
import java.util.UUID;

public record WorkInstructionSummaryDto(
        UUID id,
        String identifier,
        UUID revisionId,
        Integer revision,
        String revisionStatus,
        String title,
        boolean hasDraft,
        Map<String, Object> customFields) {
}
