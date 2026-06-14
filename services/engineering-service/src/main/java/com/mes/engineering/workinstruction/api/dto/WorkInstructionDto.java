package com.mes.engineering.workinstruction.api.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Full detail view: header + the displayed revision's steps and (if any) signatures. */
public record WorkInstructionDto(
        UUID id,
        String identifier,
        UUID revisionId,
        Integer revision,
        String revisionStatus,
        String title,
        String description,
        String partContext,
        String reasonForRevision,
        boolean hasDraft,
        boolean hasPending,
        Map<String, Object> customFields,
        String submittedBy,
        String approvedBy,
        String rejectedBy,
        String rejectionReason,
        List<StepDto> steps,
        List<SignatureDto> signatures) {
}
