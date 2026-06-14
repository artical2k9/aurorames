package com.mes.quality.inspectionplan.api.dto;

import com.mes.quality.inspectionplan.domain.InspectionCharacteristic;

public final class CharacteristicMapper {

    private CharacteristicMapper() {
    }

    public static CharacteristicDto toDto(InspectionCharacteristic c) {
        return new CharacteristicDto(
                c.getId(),
                c.getCharacteristicNumber(),
                c.getName(),
                c.getDescription(),
                c.getSource(),
                c.getCharacteristicType(),
                c.getInspectionMethod(),
                c.getGaugeType(),
                c.getUnitOfMeasure(),
                c.getSampleSizeRule(),
                c.getSampleSizeCount(),
                c.getRecordingBasis(),
                c.getNominalValue(),
                c.getLowerLimit(),
                c.getUpperLimit(),
                c.getExpectedBoolean(),
                c.getExpression(),
                c.getCustomFields());
    }
}
