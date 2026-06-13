package com.mes.quality.inspectionplan.api.dto;

import com.mes.quality.inspectionplan.domain.RevisionStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record InspectionPlanSummaryDto(
        UUID id,
        UUID itemId,
        String partNumber,
        UUID revisionId,
        Integer revision,
        RevisionStatus revisionStatus,
        String name,
        boolean hasDraft,
        Map<String, Object> customFields,
        String createdBy,
        Instant createdAt) {
}
