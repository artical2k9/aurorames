package com.mes.iam.api.dto;

import java.time.Instant;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        String name,
        String description,
        boolean systemRole,
        long privilegeCount,
        Instant createdAt,
        String createdBy
) {}
