package com.mikemes.auditservice.api.dto;

import java.util.UUID;

public record VerificationViolation(
        UUID recordId,
        String reason,
        String expectedChecksum,
        String actualChecksum
) {
}
