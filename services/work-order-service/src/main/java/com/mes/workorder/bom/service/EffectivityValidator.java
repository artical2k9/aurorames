package com.mes.workorder.bom.service;

import com.mes.workorder.bom.api.dto.CreateBomLineRequest;
import com.mes.workorder.bom.domain.BomLine;
import com.mes.workorder.bom.domain.EffectivityMethod;
import com.mes.workorder.bom.repository.BomLineRepository;
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
        if (req.getEffectivityMethod() != EffectivityMethod.DATE) {
            return;
        }
        List<BomLine> existing = bomLineRepository.findAllByBomIdAndFindNumber(bomId, req.getFindNumber());
        LocalDate newFrom = req.getEffectiveFromDate();
        LocalDate newTo = req.getEffectiveToDate() != null ? req.getEffectiveToDate() : LocalDate.MAX;

        for (BomLine line : existing) {
            if (line.getEffectivityMethod() != EffectivityMethod.DATE) {
                continue;
            }
            LocalDate existingFrom = line.getEffectiveFromDate();
            LocalDate existingTo = line.getEffectiveToDate() != null ? line.getEffectiveToDate() : LocalDate.MAX;

            if (existingFrom == null) {
                continue;
            }
            // Overlap: newFrom <= existingTo AND existingFrom <= newTo
            if (!newFrom.isAfter(existingTo) && !existingFrom.isAfter(newTo)) {
                throw new EffectivityOverlapException(req.getFindNumber(), line.getId());
            }
        }
    }
}
