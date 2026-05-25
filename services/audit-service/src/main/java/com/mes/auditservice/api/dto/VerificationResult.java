package com.mes.auditservice.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record VerificationResult(
        String status,
        long recordsChecked,
        List<VerificationViolation> violations,
        OffsetDateTime verifiedAt
) {
}
