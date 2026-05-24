package com.mikemes.auditservice.api.dto;

import java.time.OffsetDateTime;

public record ErrorResponse(
        int status,
        String error,
        String message,
        OffsetDateTime timestamp
) {
}
