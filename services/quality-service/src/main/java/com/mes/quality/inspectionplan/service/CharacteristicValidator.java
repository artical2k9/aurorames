package com.mes.quality.inspectionplan.service;

import com.mes.quality.inspectionplan.domain.CharacteristicType;
import com.mes.quality.inspectionplan.domain.SampleSizeRule;
import com.mes.quality.service.QualityValidationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Type-field matrix validation for inspection characteristics (data-model.md):
 *
 * <pre>
 * Field            SPECIFIC              COMMON      CALCULATED
 * nominal/limits   required (≥1 limit)   rejected    optional
 * expected_boolean rejected              required    rejected
 * expression       rejected              rejected    required
 * </pre>
 *
 * Plus: SPECIFIC limits must order lower ≤ nominal ≤ upper; FIXED_COUNT requires count ≥ 1.
 */
public final class CharacteristicValidator {

    private CharacteristicValidator() {
    }

    public static void validate(CharacteristicType type,
                                BigDecimal nominalValue,
                                BigDecimal lowerLimit,
                                BigDecimal upperLimit,
                                Boolean expectedBoolean,
                                String expression,
                                SampleSizeRule sampleSizeRule,
                                Integer sampleSizeCount) {
        List<String> problems = new ArrayList<>();

        switch (type) {
            case SPECIFIC -> validateSpecific(nominalValue, lowerLimit, upperLimit,
                    expectedBoolean, expression, problems);
            case COMMON -> validateCommon(nominalValue, lowerLimit, upperLimit,
                    expectedBoolean, expression, problems);
            case CALCULATED -> validateCalculated(expectedBoolean, expression, problems);
            default -> problems.add("Unknown characteristic type");
        }

        if (sampleSizeRule == SampleSizeRule.FIXED_COUNT
                && (sampleSizeCount == null || sampleSizeCount < 1)) {
            problems.add("sampleSizeCount must be at least 1 when sampleSizeRule is FIXED_COUNT");
        }

        if (!problems.isEmpty()) {
            throw new QualityValidationException("Invalid characteristic definition", problems);
        }
    }

    private static void validateSpecific(BigDecimal nominal, BigDecimal lower, BigDecimal upper,
                                         Boolean expectedBoolean, String expression,
                                         List<String> problems) {
        if (nominal == null && lower == null && upper == null) {
            problems.add("SPECIFIC characteristic requires at least one of nominalValue/lowerLimit/upperLimit");
        }
        if (expectedBoolean != null) {
            problems.add("expectedBoolean is not allowed on a SPECIFIC characteristic");
        }
        if (expression != null && !expression.isBlank()) {
            problems.add("expression is not allowed on a SPECIFIC characteristic");
        }
        if (lower != null && upper != null && lower.compareTo(upper) > 0) {
            problems.add("lowerLimit must be ≤ upperLimit");
        }
        if (lower != null && nominal != null && lower.compareTo(nominal) > 0) {
            problems.add("lowerLimit must be ≤ nominalValue");
        }
        if (upper != null && nominal != null && nominal.compareTo(upper) > 0) {
            problems.add("nominalValue must be ≤ upperLimit");
        }
    }

    private static void validateCommon(BigDecimal nominal, BigDecimal lower, BigDecimal upper,
                                       Boolean expectedBoolean, String expression,
                                       List<String> problems) {
        if (expectedBoolean == null) {
            problems.add("COMMON characteristic requires expectedBoolean");
        }
        if (nominal != null || lower != null || upper != null) {
            problems.add("nominalValue/limits are not allowed on a COMMON characteristic");
        }
        if (expression != null && !expression.isBlank()) {
            problems.add("expression is not allowed on a COMMON characteristic");
        }
    }

    private static void validateCalculated(Boolean expectedBoolean, String expression,
                                           List<String> problems) {
        if (expression == null || expression.isBlank()) {
            problems.add("CALCULATED characteristic requires expression");
        }
        if (expectedBoolean != null) {
            problems.add("expectedBoolean is not allowed on a CALCULATED characteristic");
        }
    }
}
