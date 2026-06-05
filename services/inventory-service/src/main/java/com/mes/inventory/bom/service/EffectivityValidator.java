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

    public void validateNewLine(UUID bomId, CreateBomLineRequest req) {
        validateDateOverlap(bomId, req.getFindNumber(), req.getEffectivityMethod(),
                req.getEffectiveFromDate(), req.getEffectiveToDate(), null);
    }

    public void validateUpdateLine(UUID bomId, String findNumber, EffectivityMethod method,
                                   LocalDate fromDate, LocalDate toDate, UUID excludeLineId) {
        validateDateOverlap(bomId, findNumber, method, fromDate, toDate, excludeLineId);
    }

    private void validateDateOverlap(UUID bomId, String findNumber, EffectivityMethod method,
                                     LocalDate fromDate, LocalDate toDate, UUID excludeLineId) {
        if (method != EffectivityMethod.DATE) {
            return;
        }
        List<BomLine> existing = bomLineRepository.findAllByBomIdAndFindNumber(bomId, findNumber);
        LocalDate newFrom = fromDate;
        LocalDate newTo = toDate != null ? toDate : LocalDate.MAX;

        for (BomLine line : existing) {
            LocalDate existingFrom = line.getEffectiveFromDate();
            if ((excludeLineId != null && line.getId().equals(excludeLineId))
                    || line.getEffectivityMethod() != EffectivityMethod.DATE
                    || existingFrom == null) {
                continue;
            }
            LocalDate existingTo = line.getEffectiveToDate() != null ? line.getEffectiveToDate() : LocalDate.MAX;
            // Overlap: newFrom <= existingTo AND existingFrom <= newTo
            if (!newFrom.isAfter(existingTo) && !existingFrom.isAfter(newTo)) {
                throw new EffectivityOverlapException(findNumber, line.getId());
            }
        }
    }
}
