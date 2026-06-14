package com.mes.quality.inspectionplan.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Type;
import org.hibernate.envers.Audited;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Audited
@EntityListeners(AuditingEntityListener.class)
@Table(name = "inspection_characteristic", schema = "quality")
public class InspectionCharacteristic {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_revision_id", nullable = false)
    private InspectionPlanRevision planRevision;

    @Column(name = "characteristic_number", nullable = false)
    private Integer characteristicNumber;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private CharacteristicSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "characteristic_type", nullable = false, length = 20)
    private CharacteristicType characteristicType;

    @Column(name = "inspection_method", length = 255)
    private String inspectionMethod;

    @Column(name = "gauge_type", length = 255)
    private String gaugeType;

    @Column(name = "unit_of_measure", length = 20)
    private String unitOfMeasure;

    @Enumerated(EnumType.STRING)
    @Column(name = "sample_size_rule", nullable = false, length = 20)
    private SampleSizeRule sampleSizeRule;

    @Column(name = "sample_size_count")
    private Integer sampleSizeCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "recording_basis", nullable = false, length = 20)
    private RecordingBasis recordingBasis;

    @Column(name = "nominal_value", precision = 18, scale = 6)
    private BigDecimal nominalValue;

    @Column(name = "lower_limit", precision = 18, scale = 6)
    private BigDecimal lowerLimit;

    @Column(name = "upper_limit", precision = 18, scale = 6)
    private BigDecimal upperLimit;

    @Column(name = "expected_boolean")
    private Boolean expectedBoolean;

    @Column(name = "expression", length = 1000)
    private String expression;

    @Type(JsonBinaryType.class)
    @Column(name = "custom_fields", columnDefinition = "jsonb")
    private Map<String, Object> customFields;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 255)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedBy
    @Column(name = "modified_by", nullable = false, length = 255)
    private String modifiedBy;

    @LastModifiedDate
    @Column(name = "modified_at", nullable = false)
    private Instant modifiedAt;

    public UUID getId() {
        return id;
    }

    public InspectionPlanRevision getPlanRevision() {
        return planRevision;
    }

    public void setPlanRevision(InspectionPlanRevision planRevision) {
        this.planRevision = planRevision;
    }

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

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getModifiedBy() {
        return modifiedBy;
    }

    public Instant getModifiedAt() {
        return modifiedAt;
    }
}
