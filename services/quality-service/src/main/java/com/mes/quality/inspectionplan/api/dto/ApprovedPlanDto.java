package com.mes.quality.inspectionplan.api.dto;

import java.util.List;
import java.util.UUID;

/** Consumer view (MES-9): the latest approved revision of a plan plus its characteristics. */
public record ApprovedPlanDto(
        UUID planId,
        UUID itemId,
        String partNumber,
        Integer revision,
        String name,
        List<CharacteristicDto> characteristics) {
}
