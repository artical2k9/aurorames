package com.mes.quality.inspectionplan.api.dto;

import com.mes.quality.inspectionplan.domain.CharacteristicSource;
import com.mes.quality.inspectionplan.domain.CharacteristicType;
import com.mes.quality.inspectionplan.domain.RecordingBasis;
import com.mes.quality.inspectionplan.domain.SampleSizeRule;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record CharacteristicDto(
        UUID id,
        Integer characteristicNumber,
        String name,
        String description,
        CharacteristicSource source,
        CharacteristicType characteristicType,
        String inspectionMethod,
        String gaugeType,
        String unitOfMeasure,
        SampleSizeRule sampleSizeRule,
        Integer sampleSizeCount,
        RecordingBasis recordingBasis,
        BigDecimal nominalValue,
        BigDecimal lowerLimit,
        BigDecimal upperLimit,
        Boolean expectedBoolean,
        String expression,
        Map<String, Object> customFields) {
}
