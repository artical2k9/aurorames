package com.mes.engineering.workinstruction.api.dto;

import java.time.Instant;
import java.util.UUID;

public record SignatureDto(
        UUID id,
        String signerUserId,
        String signerFullName,
        Instant signedAt,
        String meaning) {
}
