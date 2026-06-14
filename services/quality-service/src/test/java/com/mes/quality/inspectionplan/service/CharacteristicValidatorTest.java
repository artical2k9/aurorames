package com.mes.quality.inspectionplan.service;

import com.mes.quality.inspectionplan.domain.CharacteristicType;
import com.mes.quality.inspectionplan.domain.SampleSizeRule;
import com.mes.quality.service.QualityValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CharacteristicValidatorTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void specificWithOrderedLimitsPasses() {
        assertThatCode(() -> CharacteristicValidator.validate(CharacteristicType.SPECIFIC,
                bd("25.40"), bd("25.38"), bd("25.42"), null, null, SampleSizeRule.ALL, null))
                .doesNotThrowAnyException();
    }

    @Test
    void specificRequiresAtLeastOneLimit() {
        assertThatThrownBy(() -> CharacteristicValidator.validate(CharacteristicType.SPECIFIC,
                null, null, null, null, null, SampleSizeRule.ALL, null))
                .isInstanceOf(QualityValidationException.class);
    }

    @Test
    void specificRejectsInvertedLimits() {
        assertThatThrownBy(() -> CharacteristicValidator.validate(CharacteristicType.SPECIFIC,
                bd("25.40"), bd("25.50"), bd("25.30"), null, null, SampleSizeRule.ALL, null))
                .isInstanceOf(QualityValidationException.class);
    }

    @Test
    void specificRejectsExpectedBoolean() {
        assertThatThrownBy(() -> CharacteristicValidator.validate(CharacteristicType.SPECIFIC,
                bd("1"), null, null, Boolean.TRUE, null, SampleSizeRule.ALL, null))
                .isInstanceOf(QualityValidationException.class);
    }

    @Test
    void commonRequiresExpectedBoolean() {
        assertThatThrownBy(() -> CharacteristicValidator.validate(CharacteristicType.COMMON,
                null, null, null, null, null, SampleSizeRule.ALL, null))
                .isInstanceOf(QualityValidationException.class);
    }

    @Test
    void commonRejectsLimits() {
        assertThatThrownBy(() -> CharacteristicValidator.validate(CharacteristicType.COMMON,
                bd("1"), null, null, Boolean.TRUE, null, SampleSizeRule.ALL, null))
                .isInstanceOf(QualityValidationException.class);
    }

    @Test
    void commonWithExpectedBooleanPasses() {
        assertThatCode(() -> CharacteristicValidator.validate(CharacteristicType.COMMON,
                null, null, null, Boolean.TRUE, null, SampleSizeRule.ALL, null))
                .doesNotThrowAnyException();
    }

    @Test
    void calculatedRequiresExpression() {
        assertThatThrownBy(() -> CharacteristicValidator.validate(CharacteristicType.CALCULATED,
                null, null, null, null, "  ", SampleSizeRule.ALL, null))
                .isInstanceOf(QualityValidationException.class);
    }

    @Test
    void calculatedWithExpressionPasses() {
        assertThatCode(() -> CharacteristicValidator.validate(CharacteristicType.CALCULATED,
                null, null, null, null, "C10 + C20", SampleSizeRule.ALL, null))
                .doesNotThrowAnyException();
    }

    @Test
    void fixedCountRequiresPositiveCount() {
        assertThatThrownBy(() -> CharacteristicValidator.validate(CharacteristicType.SPECIFIC,
                bd("1"), null, null, null, null, SampleSizeRule.FIXED_COUNT, 0))
                .isInstanceOf(QualityValidationException.class);
    }

    @Test
    void fixedCountWithValidCountPasses() {
        assertThatCode(() -> CharacteristicValidator.validate(CharacteristicType.SPECIFIC,
                bd("1"), null, null, null, null, SampleSizeRule.FIXED_COUNT, 5))
                .doesNotThrowAnyException();
    }
}
