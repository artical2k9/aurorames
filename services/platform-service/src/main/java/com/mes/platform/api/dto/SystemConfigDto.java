package com.mes.platform.api.dto;

import com.mes.platform.domain.SystemConfiguration;

import java.time.Instant;
import java.util.UUID;

public record SystemConfigDto(
        Long id,
        UUID orgId,
        String key,
        String value,
        String description,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        String createdBy
) {
    public static SystemConfigDto from(SystemConfiguration entity) {
        return new SystemConfigDto(
                entity.getId(),
                entity.getOrgId(),
                entity.getConfigKey(),
                entity.getConfigValue(),
                entity.getDescription(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCreatedBy()
        );
    }
}
