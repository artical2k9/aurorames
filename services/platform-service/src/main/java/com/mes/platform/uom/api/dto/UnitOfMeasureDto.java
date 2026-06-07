package com.mes.platform.uom.api.dto;

import com.mes.platform.uom.domain.UnitOfMeasure;

import java.time.Instant;
import java.util.UUID;

public record UnitOfMeasureDto(
        UUID    id,
        String  code,
        String  isoCode,
        String  name,
        String  category,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static UnitOfMeasureDto from(UnitOfMeasure u) {
        return new UnitOfMeasureDto(
                u.getId(), u.getCode(), u.getIsoCode(),
                u.getName(), u.getCategory(), u.isActive(),
                u.getCreatedAt(), u.getUpdatedAt()
        );
    }
}
