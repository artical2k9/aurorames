package com.mes.quality.inspectionplan.api.dto;

import com.mes.quality.inspectionplan.domain.InspectionPlan;
import com.mes.quality.inspectionplan.domain.InspectionPlanRevision;

public final class InspectionPlanMapper {

    private InspectionPlanMapper() {
    }

    public static InspectionPlanDto toDto(InspectionPlan plan, InspectionPlanRevision rev,
                                          boolean hasDraft, boolean hasPendingApproval) {
        return new InspectionPlanDto(
                plan.getId(),
                plan.getOrgId(),
                plan.getItemId(),
                plan.getPartNumber(),
                rev.getId(),
                rev.getRevision(),
                rev.getRevisionStatus(),
                rev.getName(),
                rev.getDescription(),
                rev.getReasonForRevision(),
                rev.getCustomFields(),
                hasDraft,
                hasPendingApproval,
                rev.getSubmittedBy(),
                rev.getSubmittedAt(),
                rev.getApprovedBy(),
                rev.getApprovedAt(),
                rev.getRejectedBy(),
                rev.getRejectedAt(),
                rev.getRejectionReason(),
                rev.getCreatedBy(),
                rev.getCreatedAt(),
                rev.getModifiedBy(),
                rev.getModifiedAt());
    }

    public static InspectionPlanSummaryDto toSummaryDto(InspectionPlan plan, InspectionPlanRevision rev,
                                                        boolean hasDraft) {
        return new InspectionPlanSummaryDto(
                plan.getId(),
                plan.getItemId(),
                plan.getPartNumber(),
                rev.getId(),
                rev.getRevision(),
                rev.getRevisionStatus(),
                rev.getName(),
                hasDraft,
                rev.getCustomFields(),
                plan.getCreatedBy(),
                plan.getCreatedAt());
    }

    public static InspectionPlanRevisionSummaryDto toRevisionSummaryDto(InspectionPlanRevision rev) {
        return new InspectionPlanRevisionSummaryDto(
                rev.getId(),
                rev.getRevision(),
                rev.getRevisionStatus(),
                rev.getName(),
                rev.getReasonForRevision(),
                rev.getSubmittedBy(),
                rev.getSubmittedAt(),
                rev.getApprovedBy(),
                rev.getApprovedAt(),
                rev.getCreatedBy(),
                rev.getCreatedAt());
    }
}
