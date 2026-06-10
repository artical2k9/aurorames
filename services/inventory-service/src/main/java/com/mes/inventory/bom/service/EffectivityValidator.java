package com.mes.inventory.bom.service;

import com.mes.inventory.bom.api.dto.CreateBomLineRequest;
import com.mes.inventory.bom.domain.BomLine;
import com.mes.inventory.bom.domain.EffectivityMethod;
import com.mes.inventory.bom.repository.BomLineRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
public class EffectivityValidator {

    private final BomLineRepository bomLineRepository;

    public EffectivityValidator(BomLineRepository bomLineRepository) {
        this.bomLineRepository = bomLineRepository;
    }

    public void validateNewLine(UUID bomRevisionId, CreateBomLineRequest req) {
        validateDateOverlap(bomRevisionId, req.getFindNumber(), req.getEffectivityMethod(),
                req.getEffectiveFromDate(), req.getEffectiveToDate(), null);
    }

    public void validateUpdateLine(UUID bomRevisionId, String findNumber, EffectivityMethod method,
                                   LocalDate fromDate, LocalDate toDate, UUID excludeLineId) {
        validateDateOverlap(bomRevisionId, findNumber, method, fromDate, toDate, excludeLineId);
    }

    private void validateDateOverlap(UUID bomRevisionId, String findNumber, EffectivityMethod method,
                                     LocalDate fromDate, LocalDate toDate, UUID excludeLineId) {
        if (method != EffectivityMethod.DATE) {
            return;
        }
        List<BomLine> existing = bomLineRepository.findAllByBomRevisionIdAndFindNumber(
                bomRevisionId, findNumber);
        LocalDate newFrom = fromDate;
        LocalDate newTo = toDate != null ? toDate : LocalDate.MAX;

        for (BomLine line : existing) {
            LocalDate existingFrom = line.getEffectiveFromDate();
            if ((excludeLineId != null && line.getId().equals(excludeLineId))
                    || line.getEffectivityMethod() != EffectivityMethod.DATE
                    || existingFrom == null) {
                continue;
            }
            LocalDate existingTo = line.getEffectiveToDate() != null
                    ? line.getEffectiveToDate() : LocalDate.MAX;
            if (!newFrom.isAfter(existingTo) && !existingFrom.isAfter(newTo)) {
                throw new EffectivityOverlapException(findNumber, line.getId());
            }
        }
    }
}
