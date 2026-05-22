package com.mikemes.iam.api.dto;

import java.time.Instant;
import java.util.UUID;

public record PrivilegeResponse(
        UUID id,
        String privilegeKey,
        String moduleName,
        String description,
        Instant registeredAt
) {}
