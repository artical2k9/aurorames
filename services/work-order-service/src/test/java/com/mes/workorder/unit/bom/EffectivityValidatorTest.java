package com.mes.workorder.unit.bom;

import com.mes.workorder.bom.api.dto.CreateBomLineRequest;
import com.mes.workorder.bom.domain.BomLine;
import com.mes.workorder.bom.domain.EffectivityMethod;
import com.mes.workorder.bom.repository.BomLineRepository;
import com.mes.workorder.bom.service.EffectivityOverlapException;
import com.mes.workorder.bom.service.EffectivityValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EffectivityValidatorTest {

    @Mock BomLineRepository bomLineRepository;

    @InjectMocks
    EffectivityValidator effectivityValidator;

    UUID bomId = UUID.randomUUID();

    @Test
    void overlapOnDateRangeThrowsEffectivityOverlapException() {
        BomLine existing = dateLine("010", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
        when(bomLineRepository.findAllByBomIdAndFindNumber(bomId, "010")).thenReturn(List.of(existing));

        CreateBomLineRequest req = dateLineRequest("010", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 6, 1));

        assertThatThrownBy(() -> effectivityValidator.validateNewLine(bomId, req))
                .isInstanceOf(EffectivityOverlapException.class)
                .hasMessageContaining("010")
                .hasMessageContaining(existing.getId().toString());
    }

    @Test
    void openEndedExistingLineOverlapsAnyFutureLine() {
        BomLine existing = dateLine("010", LocalDate.of(2025, 1, 1), null); // null = open-ended
        when(bomLineRepository.findAllByBomIdAndFindNumber(bomId, "010")).thenReturn(List.of(existing));

        CreateBomLineRequest req = dateLineRequest("010", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThatThrownBy(() -> effectivityValidator.validateNewLine(bomId, req))
                .isInstanceOf(EffectivityOverlapException.class);
    }

    @Test
    void noEffectivityMethodSkipsOverlapCheck() {
        CreateBomLineRequest req = new CreateBomLineRequest();
        req.setComponentItemId(UUID.randomUUID());
        req.setQuantity(BigDecimal.ONE);
        req.setUnitOfMeasure("EA");
        req.setFindNumber("010");
        // effectivityMethod not set

        assertThatNoException().isThrownBy(() -> effectivityValidator.validateNewLine(bomId, req));
    }

    @Test
    void unitMethodWithNullEffectiveToUnitIsValid() {
        CreateBomLineRequest req = new CreateBomLineRequest();
        req.setComponentItemId(UUID.randomUUID());
        req.setQuantity(BigDecimal.ONE);
        req.setUnitOfMeasure("EA");
        req.setFindNumber("010");
        req.setEffectivityMethod(EffectivityMethod.UNIT);
        req.setEffectiveFromUnit("100");
        req.setEffectiveToUnit(null); // open-ended unit range

        assertThatNoException().isThrownBy(() -> effectivityValidator.validateNewLine(bomId, req));
    }

    @Test
    void nonOverlappingDateLinesAreAllowed() {
        BomLine existing = dateLine("010", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 30));
        when(bomLineRepository.findAllByBomIdAndFindNumber(bomId, "010")).thenReturn(List.of(existing));

        CreateBomLineRequest req = dateLineRequest("010", LocalDate.of(2025, 7, 1), LocalDate.of(2025, 12, 31));

        assertThatNoException().isThrownBy(() -> effectivityValidator.validateNewLine(bomId, req));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private BomLine dateLine(String findNumber, LocalDate from, LocalDate to) {
        BomLine line = new BomLine();
        line.setBomId(bomId);
        line.setComponentItemId(UUID.randomUUID());
        line.setQuantity(BigDecimal.ONE);
        line.setUnitOfMeasure("EA");
        line.setFindNumber(findNumber);
        line.setEffectivityMethod(EffectivityMethod.DATE);
        line.setEffectiveFromDate(from);
        line.setEffectiveToDate(to);
        return line;
    }

    private CreateBomLineRequest dateLineRequest(String findNumber, LocalDate from, LocalDate to) {
        CreateBomLineRequest req = new CreateBomLineRequest();
        req.setComponentItemId(UUID.randomUUID());
        req.setQuantity(BigDecimal.ONE);
        req.setUnitOfMeasure("EA");
        req.setFindNumber(findNumber);
        req.setEffectivityMethod(EffectivityMethod.DATE);
        req.setEffectiveFromDate(from);
        req.setEffectiveToDate(to);
        return req;
    }
}
