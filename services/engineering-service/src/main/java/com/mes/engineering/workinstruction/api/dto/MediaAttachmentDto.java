package com.mes.engineering.workinstruction.api.dto;

import java.util.UUID;

public record MediaAttachmentDto(
        UUID id,
        UUID stepId,
        String fileName,
        String contentType,
        long sizeBytes,
        String caption,
        int displayOrder) {
}
