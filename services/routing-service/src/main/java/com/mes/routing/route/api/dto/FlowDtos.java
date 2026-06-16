package com.mes.routing.route.api.dto;

import com.mes.routing.route.domain.MutuallyExclusiveLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Request/response DTOs for advanced flow control: groups (US5), steps (US6), ME sets (US4/US5/US6). */
public final class FlowDtos {

    private FlowDtos() {
    }

    // ── Groups (US5) ──────────────────────────────────────────────────────────────

    /** One group in a full replace of a route's groups; {@code operationIds} are reassigned to it. */
    public record GroupInput(
            @Size(max = 255) String name,
            @NotNull Integer groupSequenceNumber,
            boolean optional,
            List<UUID> operationIds) {
    }

    public record PutGroupsRequest(@Valid @NotNull List<GroupInput> groups) {
    }

    public record GroupDto(
            UUID id,
            String name,
            int groupSequenceNumber,
            boolean optional,
            String derivedType,
            List<UUID> operationIds) {
    }

    // ── Steps (US6) ───────────────────────────────────────────────────────────────

    public record CreateStepRequest(
            @NotNull Integer stepNumber,
            @NotNull Integer stepSequenceNumber,
            @Size(max = 500) String description,
            boolean optional) {
    }

    public record PatchStepRequest(
            Integer stepNumber,
            Integer stepSequenceNumber,
            @Size(max = 500) String description,
            Boolean optional) {
    }

    public record StepDto(
            UUID id,
            int stepNumber,
            int stepSequenceNumber,
            String description,
            boolean optional,
            String derivedType) {
    }

    // ── Mutually-exclusive sets (US4/US5/US6) ──────────────────────────────────────

    /** A mutually-exclusive subset; the parallel {@code sequenceNumber} is derived from members. */
    public record MutuallyExclusiveSetInput(
            @NotNull MutuallyExclusiveLevel level,
            @NotNull @Size(min = 2) List<UUID> memberIds) {
    }

    public record PutMutuallyExclusiveSetsRequest(@Valid @NotNull List<MutuallyExclusiveSetInput> sets) {
    }

    public record MutuallyExclusiveSetDto(
            UUID id,
            MutuallyExclusiveLevel level,
            int sequenceNumber,
            List<UUID> memberIds) {
    }
}
