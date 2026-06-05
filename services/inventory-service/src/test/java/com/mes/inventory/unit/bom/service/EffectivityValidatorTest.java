package com.mes.inventory.unit.bom.service;

import com.mes.inventory.bom.api.dto.CreateBomLineRequest;
import com.mes.inventory.bom.domain.BomLine;
import com.mes.inventory.bom.domain.EffectivityMethod;
import com.mes.inventory.bom.repository.BomLineRepository;
import com.mes.inventory.bom.service.EffectivityOverlapException;
import com.mes.inventory.bom.service.EffectivityValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EffectivityValidatorTest {

    @Mock
    BomLineRepository bomLineRepository;

    @InjectMocks
    EffectivityValidator validator;

    @Test
    void validateNewLine_passesWithoutQuery_whenMethodIsUnit() {
        CreateBomLineRequest req = new CreateBomLineRequest();
        req.setEffectivityMethod(EffectivityMethod.UNIT);
        req.setFindNumber("F-001");

        assertThatCode(() -> validator.validateNewLine(UUID.randomUUID(), req))
                .doesNotThrowAnyException();
    }

    @Test
    void validateNewLine_passes_whenNoExistingLinesForFindNumber() {
        UUID bomId = UUID.randomUUID();
        CreateBomLineRequest req = new CreateBomLineRequest();
        req.setEffectivityMethod(EffectivityMethod.DATE);
        req.setFindNumber("F-001");
        req.setEffectiveFromDate(LocalDate.of(2025, 1, 1));

        when(bomLineRepository.findAllByBomIdAndFindNumber(bomId, "F-001"))
                .thenReturn(Collections.emptyList());

        assertThatCode(() -> validator.validateNewLine(bomId, req))
                .doesNotThrowAnyException();
    }

    @Test
    void validateNewLine_passes_whenNewRangeDoesNotOverlap() {
        UUID bomId = UUID.randomUUID();

        BomLine existing = new BomLine();
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
        existing.setEffectivityMethod(EffectivityMethod.DATE);
        existing.setEffectiveFromDate(LocalDate.of(2025, 1, 1));
        existing.setEffectiveToDate(LocalDate.of(2025, 6, 30));

        CreateBomLineRequest req = new CreateBomLineRequest();
        req.setEffectivityMethod(EffectivityMethod.DATE);
        req.setFindNumber("F-001");
        req.setEffectiveFromDate(LocalDate.of(2025, 7, 1));
        req.setEffectiveToDate(LocalDate.of(2025, 12, 31));

        when(bomLineRepository.findAllByBomIdAndFindNumber(bomId, "F-001"))
                .thenReturn(List.of(existing));

        assertThatCode(() -> validator.validateNewLine(bomId, req))
                .doesNotThrowAnyException();
    }

    @Test
    void validateNewLine_throwsOverlapException_whenRangesOverlap() {
        UUID bomId = UUID.randomUUID();

        BomLine existing = new BomLine();
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
        existing.setEffectivityMethod(EffectivityMethod.DATE);
        existing.setEffectiveFromDate(LocalDate.of(2025, 1, 1));
        existing.setEffectiveToDate(LocalDate.of(2025, 12, 31));

        CreateBomLineRequest req = new CreateBomLineRequest();
        req.setEffectivityMethod(EffectivityMethod.DATE);
        req.setFindNumber("F-001");
        req.setEffectiveFromDate(LocalDate.of(2025, 6, 1));
        req.setEffectiveToDate(LocalDate.of(2025, 9, 30));

        when(bomLineRepository.findAllByBomIdAndFindNumber(bomId, "F-001"))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> validator.validateNewLine(bomId, req))
                .isInstanceOf(EffectivityOverlapException.class);
    }

    @Test
    void validateNewLine_throwsOverlapException_whenBothRangesAreOpenEnded() {
        UUID bomId = UUID.randomUUID();

        BomLine existing = new BomLine();
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
        existing.setEffectivityMethod(EffectivityMethod.DATE);
        existing.setEffectiveFromDate(LocalDate.of(2025, 6, 1));
        existing.setEffectiveToDate(null);

        CreateBomLineRequest req = new CreateBomLineRequest();
        req.setEffectivityMethod(EffectivityMethod.DATE);
        req.setFindNumber("F-001");
        req.setEffectiveFromDate(LocalDate.of(2026, 1, 1));
        req.setEffectiveToDate(null);

        when(bomLineRepository.findAllByBomIdAndFindNumber(bomId, "F-001"))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> validator.validateNewLine(bomId, req))
                .isInstanceOf(EffectivityOverlapException.class);
    }

    @Test
    void validateUpdateLine_skipsLineMatchingExcludeId() {
        UUID bomId = UUID.randomUUID();
        UUID excludeId = UUID.randomUUID();

        BomLine excluded = new BomLine();
        ReflectionTestUtils.setField(excluded, "id", excludeId);
        excluded.setEffectivityMethod(EffectivityMethod.DATE);
        excluded.setEffectiveFromDate(LocalDate.of(2025, 1, 1));
        excluded.setEffectiveToDate(LocalDate.of(2025, 12, 31));

        when(bomLineRepository.findAllByBomIdAndFindNumber(bomId, "F-001"))
                .thenReturn(List.of(excluded));

        assertThatCode(() -> validator.validateUpdateLine(
                bomId, "F-001", EffectivityMethod.DATE,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), excludeId))
                .doesNotThrowAnyException();
    }

    @Test
    void validateUpdateLine_skipsExistingLineWithNonDateEffectivity() {
        UUID bomId = UUID.randomUUID();

        BomLine unitLine = new BomLine();
        ReflectionTestUtils.setField(unitLine, "id", UUID.randomUUID());
        unitLine.setEffectivityMethod(EffectivityMethod.UNIT);
        unitLine.setEffectiveFromDate(null);

        when(bomLineRepository.findAllByBomIdAndFindNumber(bomId, "F-001"))
                .thenReturn(List.of(unitLine));

        assertThatCode(() -> validator.validateUpdateLine(
                bomId, "F-001", EffectivityMethod.DATE,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), null))
                .doesNotThrowAnyException();
    }

    @Test
    void validateUpdateLine_skipsExistingLineWithNullFromDate() {
        UUID bomId = UUID.randomUUID();

        BomLine nullFromLine = new BomLine();
        ReflectionTestUtils.setField(nullFromLine, "id", UUID.randomUUID());
        nullFromLine.setEffectivityMethod(EffectivityMethod.DATE);
        nullFromLine.setEffectiveFromDate(null);

        when(bomLineRepository.findAllByBomIdAndFindNumber(bomId, "F-001"))
                .thenReturn(List.of(nullFromLine));

        assertThatCode(() -> validator.validateUpdateLine(
                bomId, "F-001", EffectivityMethod.DATE,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), null))
                .doesNotThrowAnyException();
    }
}
