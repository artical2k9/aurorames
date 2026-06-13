package com.mes.quality.inspectionplan.service;

import com.mes.quality.inspectionplan.domain.CharacteristicType;
import com.mes.quality.service.QualityValidationException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpressionValidatorTest {

    // ── parseReferences / grammar ─────────────────────────────────────────────

    @Test
    void parsesOperatorsParensNumbersAndRefs() {
        Set<Integer> refs = ExpressionValidator.parseReferences("(C10 + C20) / 2 - 1.5");
        assertThat(refs).containsExactlyInAnyOrder(10, 20);
    }

    @Test
    void parsesHistorianTag() {
        Set<Integer> refs = ExpressionValidator.parseReferences("#{furnace1.temp} - C10");
        assertThat(refs).containsExactly(10);
    }

    @Test
    void blankExpressionRejected() {
        assertThatThrownBy(() -> ExpressionValidator.parseReferences("  "))
                .isInstanceOf(QualityValidationException.class);
    }

    @Test
    void unbalancedParensRejected() {
        assertThatThrownBy(() -> ExpressionValidator.parseReferences("(C10 + C20"))
                .isInstanceOf(QualityValidationException.class);
    }

    @Test
    void trailingOperatorRejected() {
        assertThatThrownBy(() -> ExpressionValidator.parseReferences("C10 +"))
                .isInstanceOf(QualityValidationException.class);
    }

    @Test
    void unexpectedCharacterRejected() {
        assertThatThrownBy(() -> ExpressionValidator.parseReferences("C10 & C20"))
                .isInstanceOf(QualityValidationException.class);
    }

    // ── validate: reference resolution & type rules ───────────────────────────

    @Test
    void validChainPasses() {
        Map<Integer, CharacteristicType> types = Map.of(
                10, CharacteristicType.SPECIFIC,
                20, CharacteristicType.SPECIFIC,
                30, CharacteristicType.CALCULATED);
        Map<Integer, String> exprs = Map.of(30, "(C10 + C20) / 2");
        assertThatCode(() -> ExpressionValidator.validate(30, "(C10 + C20) / 2", types, exprs))
                .doesNotThrowAnyException();
    }

    @Test
    void unknownReferenceRejected() {
        Map<Integer, CharacteristicType> types = Map.of(30, CharacteristicType.CALCULATED);
        Map<Integer, String> exprs = Map.of(30, "C10 + 1");
        assertThatThrownBy(() -> ExpressionValidator.validate(30, "C10 + 1", types, exprs))
                .isInstanceOf(QualityValidationException.class)
                .satisfies(ex -> assertThat(((QualityValidationException) ex).getDetails())
                        .anyMatch(d -> d.contains("C10") && d.contains("does not exist")));
    }

    @Test
    void selfReferenceRejected() {
        Map<Integer, CharacteristicType> types = Map.of(30, CharacteristicType.CALCULATED);
        Map<Integer, String> exprs = Map.of(30, "C30 + 1");
        assertThatThrownBy(() -> ExpressionValidator.validate(30, "C30 + 1", types, exprs))
                .isInstanceOf(QualityValidationException.class)
                .satisfies(ex -> assertThat(((QualityValidationException) ex).getDetails())
                        .anyMatch(d -> d.contains("references itself")));
    }

    @Test
    void referenceToCommonRejected() {
        Map<Integer, CharacteristicType> types = Map.of(
                10, CharacteristicType.COMMON,
                30, CharacteristicType.CALCULATED);
        Map<Integer, String> exprs = Map.of(30, "C10 + 1");
        assertThatThrownBy(() -> ExpressionValidator.validate(30, "C10 + 1", types, exprs))
                .isInstanceOf(QualityValidationException.class)
                .satisfies(ex -> assertThat(((QualityValidationException) ex).getDetails())
                        .anyMatch(d -> d.contains("COMMON")));
    }

    // ── cycle detection ───────────────────────────────────────────────────────

    @Test
    void twoNodeCycleRejected() {
        Map<Integer, CharacteristicType> types = Map.of(
                10, CharacteristicType.CALCULATED,
                20, CharacteristicType.CALCULATED);
        Map<Integer, String> exprs = Map.of(10, "C20 + 1", 20, "C10 + 1");
        assertThatThrownBy(() -> ExpressionValidator.validate(10, "C20 + 1", types, exprs))
                .isInstanceOf(QualityValidationException.class)
                .satisfies(ex -> assertThat(((QualityValidationException) ex).getDetails())
                        .anyMatch(d -> d.contains("cycle")));
    }

    @Test
    void threeNodeCycleRejected() {
        Map<Integer, CharacteristicType> types = Map.of(
                10, CharacteristicType.CALCULATED,
                20, CharacteristicType.CALCULATED,
                30, CharacteristicType.CALCULATED);
        Map<Integer, String> exprs = Map.of(10, "C20", 20, "C30", 30, "C10");
        assertThatThrownBy(() -> ExpressionValidator.validate(10, "C20", types, exprs))
                .isInstanceOf(QualityValidationException.class)
                .satisfies(ex -> assertThat(((QualityValidationException) ex).getDetails())
                        .anyMatch(d -> d.contains("cycle")));
    }

    @Test
    void acyclicChainAcrossCalculatedPasses() {
        Map<Integer, CharacteristicType> types = Map.of(
                10, CharacteristicType.SPECIFIC,
                20, CharacteristicType.CALCULATED,
                30, CharacteristicType.CALCULATED);
        Map<Integer, String> exprs = Map.of(20, "C10 * 2", 30, "C20 + C10");
        assertThatCode(() -> ExpressionValidator.validate(30, "C20 + C10", types, exprs))
                .doesNotThrowAnyException();
    }

    @Test
    void referencesHelperDetectsUsage() {
        assertThat(ExpressionValidator.references("(C10 + C20) / 2", 20)).isTrue();
        assertThat(ExpressionValidator.references("(C10 + C20) / 2", 99)).isFalse();
        assertThat(ExpressionValidator.references(null, 10)).isFalse();
    }
}
