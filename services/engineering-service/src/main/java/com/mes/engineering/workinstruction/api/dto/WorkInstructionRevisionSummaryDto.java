package com.mes.engineering.workinstruction.api.dto;

import java.time.Instant;
import java.util.UUID;

public record WorkInstructionRevisionSummaryDto(
        UUID revisionId,
        Integer revision,
        String revisionStatus,
        String submittedBy,
        Instant submittedAt,
        String approvedBy,
        Instant approvedAt,
        String rejectedBy,
        Instant rejectedAt,
        String rejectionReason) {
}
