package com.mes.quality.inspectionplan.api.dto;

import com.mes.quality.inspectionplan.domain.RevisionStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record InspectionPlanDto(
        UUID id,
        UUID orgId,
        UUID itemId,
        String partNumber,
        UUID revisionId,
        Integer revision,
        RevisionStatus revisionStatus,
        String name,
        String description,
        String reasonForRevision,
        Map<String, Object> customFields,
        boolean hasDraft,
        boolean hasPendingApproval,
        String submittedBy,
        Instant submittedAt,
        String approvedBy,
        Instant approvedAt,
        String rejectedBy,
        Instant rejectedAt,
        String rejectionReason,
        String createdBy,
        Instant createdAt,
        String modifiedBy,
        Instant modifiedAt) {
}
