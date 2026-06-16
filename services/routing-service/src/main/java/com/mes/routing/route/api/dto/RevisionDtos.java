package com.mes.routing.route.api.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Request DTOs for two-tier revisioning (US8). */
public final class RevisionDtos {

    private RevisionDtos() {
    }

    /** Optional reason recorded when starting a route revision. */
    public record StartRevisionRequest(@Size(max = 1000) String reasonForRevision) {
    }

    /**
     * Content-only edits allowed during an operation revision. Sequence and grouping
     * ({@code operationNumber}, {@code sequenceNumber}, {@code groupId}) are intentionally absent —
     * they are locked once the route is approved and can only change in a route revision (FR-022/R7).
     */
    public record PatchOperationContentRequest(
            @Size(max = 500) String description,
            Boolean optional,
            Boolean osp,
            UUID significantProcessTypeId,
            UUID supplierId,
            Boolean clocking) {
    }
}
