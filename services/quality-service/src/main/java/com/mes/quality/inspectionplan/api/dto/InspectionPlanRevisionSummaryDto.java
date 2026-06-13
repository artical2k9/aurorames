package com.mes.quality.inspectionplan.api.dto;

import com.mes.quality.inspectionplan.domain.RevisionStatus;

import java.time.Instant;
import java.util.UUID;

public record InspectionPlanRevisionSummaryDto(
        UUID revisionId,
        Integer revision,
        RevisionStatus revisionStatus,
        String name,
        String reasonForRevision,
        String submittedBy,
        Instant submittedAt,
        String approvedBy,
        Instant approvedAt,
        String createdBy,
        Instant createdAt) {
}
