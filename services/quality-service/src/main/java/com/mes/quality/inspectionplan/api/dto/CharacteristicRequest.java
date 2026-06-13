package com.mes.quality.inspectionplan.api.dto;

import com.mes.quality.inspectionplan.domain.CharacteristicSource;
import com.mes.quality.inspectionplan.domain.CharacteristicType;
import com.mes.quality.inspectionplan.domain.RecordingBasis;
import com.mes.quality.inspectionplan.domain.SampleSizeRule;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

/** Union payload for create/patch; type-specific field rules enforced by CharacteristicValidator. */
public class CharacteristicRequest {

    @NotNull(message = "characteristicNumber is required")
    @Min(value = 1, message = "characteristicNumber must be ≥ 1")
    private Integer characteristicNumber;

    @NotBlank(message = "name is required")
    @Size(max = 200)
    private String name;

    private String description;

    @NotNull(message = "source is required")
    private CharacteristicSource source;

    @NotNull(message = "characteristicType is required")
    private CharacteristicType characteristicType;

    @Size(max = 255)
    private String inspectionMethod;

    @Size(max = 255)
    private String gaugeType;

    @Size(max = 20)
    private String unitOfMeasure;

    @NotNull(message = "sampleSizeRule is required")
    private SampleSizeRule sampleSizeRule;

    private Integer sampleSizeCount;

    private RecordingBasis recordingBasis;

    private BigDecimal nominalValue;
    private BigDecimal lowerLimit;
    private BigDecimal upperLimit;
    private Boolean expectedBoolean;

    @Size(max = 1000)
    private String expression;

    private Map<String, Object> customFields;

    public Integer getCharacteristicNumber() {
        return characteristicNumber;
    }

    public void setCharacteristicNumber(Integer characteristicNumber) {
        this.characteristicNumber = characteristicNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public CharacteristicSource getSource() {
        return source;
    }

    public void setSource(CharacteristicSource source) {
        this.source = source;
    }

    public CharacteristicType getCharacteristicType() {
        return characteristicType;
    }

    public void setCharacteristicType(CharacteristicType characteristicType) {
        this.characteristicType = characteristicType;
    }

    public String getInspectionMethod() {
        return inspectionMethod;
    }

    public void setInspectionMethod(String inspectionMethod) {
        this.inspectionMethod = inspectionMethod;
    }

    public String getGaugeType() {
        return gaugeType;
    }

    public void setGaugeType(String gaugeType) {
        this.gaugeType = gaugeType;
    }

    public String getUnitOfMeasure() {
        return unitOfMeasure;
    }

    public void setUnitOfMeasure(String unitOfMeasure) {
        this.unitOfMeasure = unitOfMeasure;
    }

    public SampleSizeRule getSampleSizeRule() {
        return sampleSizeRule;
    }

    public void setSampleSizeRule(SampleSizeRule sampleSizeRule) {
        this.sampleSizeRule = sampleSizeRule;
    }

    public Integer getSampleSizeCount() {
        return sampleSizeCount;
    }

    public void setSampleSizeCount(Integer sampleSizeCount) {
        this.sampleSizeCount = sampleSizeCount;
    }

    public RecordingBasis getRecordingBasis() {
        return recordingBasis;
    }

    public void setRecordingBasis(RecordingBasis recordingBasis) {
        this.recordingBasis = recordingBasis;
    }

    public BigDecimal getNominalValue() {
        return nominalValue;
    }

    public void setNominalValue(BigDecimal nominalValue) {
        this.nominalValue = nominalValue;
    }

    public BigDecimal getLowerLimit() {
        return lowerLimit;
    }

    public void setLowerLimit(BigDecimal lowerLimit) {
        this.lowerLimit = lowerLimit;
    }

    public BigDecimal getUpperLimit() {
        return upperLimit;
    }

    public void setUpperLimit(BigDecimal upperLimit) {
        this.upperLimit = upperLimit;
    }

    public Boolean getExpectedBoolean() {
        return expectedBoolean;
    }

    public void setExpectedBoolean(Boolean expectedBoolean) {
        this.expectedBoolean = expectedBoolean;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public Map<String, Object> getCustomFields() {
        return customFields;
    }

    public void setCustomFields(Map<String, Object> customFields) {
        this.customFields = customFields;
    }
}
