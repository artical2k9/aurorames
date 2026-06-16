package com.mes.routing.approval.api.dto;

import com.mes.routing.approval.domain.ApprovalSubjectType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request/response DTOs for route/operation approval and revision audit history (US7/US8). */
public final class ApprovalDtos {

    private ApprovalDtos() {
    }

    /**
     * An e-signature approval. {@code significantProcessTypeId} present marks this as the SME
     * approval for that significant-process type (the signer must hold its required approver role);
     * absent marks the standard route/operation approval.
     */
    public record ApproveRequest(
            @NotBlank String password,
            @NotBlank @Size(max = 100) String meaning,
            UUID significantProcessTypeId) {
    }

    public record RejectRequest(
            @NotBlank String password,
            @Size(max = 500) String reason) {
    }

    /** Outstanding requirements for a route revision to reach APPROVED. */
    public record RouteApprovalStatusDto(
            UUID routeId,
            int revision,
            String status,
            boolean standardApprovalPresent,
            List<UUID> outstandingSignificantProcessTypeIds) {
    }

    public record ApprovalRecordDto(
            UUID id,
            ApprovalSubjectType subjectType,
            UUID subjectId,
            int revision,
            String actor,
            String actorRole,
            String meaning,
            Instant signedAt,
            boolean significantProcessApprover,
            UUID significantProcessTypeId,
            Integer governingRouteRevision) {
    }

    /** Operation-revision approvals grouped under the route revision that governed them. */
    public record RouteRevisionHistoryDto(
            int routeRevision,
            List<ApprovalRecordDto> routeApprovals,
            List<ApprovalRecordDto> operationApprovals) {
    }

    public record ApprovalHistoryDto(UUID routeId, List<RouteRevisionHistoryDto> revisions) {
    }
}
