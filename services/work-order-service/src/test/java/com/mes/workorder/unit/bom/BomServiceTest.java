package com.mes.workorder.unit.bom;

import com.mes.workorder.bom.api.dto.CreateBomLineRequest;
import com.mes.workorder.bom.api.dto.UpdateBomLineRequest;
import com.mes.workorder.bom.domain.BillOfMaterials;
import com.mes.workorder.bom.domain.BomLine;
import com.mes.workorder.bom.domain.BomStatus;
import com.mes.workorder.bom.repository.BomLineRepository;
import com.mes.workorder.bom.repository.BomRepository;
import com.mes.workorder.bom.service.BomConflictException;
import com.mes.workorder.bom.service.BomNotFoundException;
import com.mes.workorder.bom.service.BomService;
import com.mes.workorder.bom.service.EffectivityValidator;
import com.mes.workorder.eco.service.EcoService;
import com.mes.workorder.itemmaster.repository.ItemMasterRepository;
import com.mes.workorder.kafka.BomEventPublisher;
import com.mes.workorder.kafka.ItemMasterEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BomServiceTest {

    @Mock BomRepository bomRepository;
    @Mock BomLineRepository bomLineRepository;
    @Mock ItemMasterRepository itemMasterRepository;
    @Mock BomEventPublisher bomEventPublisher;
    @Mock ItemMasterEventPublisher itemMasterEventPublisher;
    @Mock EffectivityValidator effectivityValidator;
    @Mock EcoService ecoService;

    @InjectMocks
    BomService bomService;

    UUID orgId = UUID.randomUUID();
    UUID bomId = UUID.randomUUID();
    UUID lineId = UUID.randomUUID();
    UUID componentId = UUID.randomUUID();

    // ── addLine tests ─────────────────────────────────────────────────────────

    @Test
    void addLineThrowsConflictWhenBomIsReleased() {
        BillOfMaterials released = new BillOfMaterials();
        released.setStatus(BomStatus.RELEASED);
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(released));

        assertThatThrownBy(() -> bomService.addLine(orgId, bomId, validLineRequest()))
                .isInstanceOf(BomConflictException.class);
    }

    @Test
    void addLineCallsCircularCheckBeforeInsert() {
        BillOfMaterials draft = new BillOfMaterials();
        draft.setStatus(BomStatus.DRAFT);
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(draft));
        when(itemMasterRepository.existsByOrgIdAndId(orgId, componentId)).thenReturn(true);
        when(bomLineRepository.existsByBomIdAndFindNumber(bomId, "010")).thenReturn(false);
        when(bomRepository.hasAncestorCycle(bomId, componentId)).thenReturn(false);
        when(bomLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        bomService.addLine(orgId, bomId, validLineRequest());

        verify(bomRepository).hasAncestorCycle(bomId, componentId);
    }

    @Test
    void addLineDuplicateFindNumberThrowsConflict() {
        BillOfMaterials draft = new BillOfMaterials();
        draft.setStatus(BomStatus.DRAFT);
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(draft));
        when(itemMasterRepository.existsByOrgIdAndId(orgId, componentId)).thenReturn(true);
        when(bomLineRepository.existsByBomIdAndFindNumber(bomId, "010")).thenReturn(true);

        assertThatThrownBy(() -> bomService.addLine(orgId, bomId, validLineRequest()))
                .isInstanceOf(BomConflictException.class);
    }

    // ── updateLine tests ──────────────────────────────────────────────────────

    @Test
    void updateLineThrowsNotFoundWhenBomMissing() {
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bomService.updateLine(orgId, bomId, lineId, new UpdateBomLineRequest()))
                .isInstanceOf(BomNotFoundException.class);
    }

    @Test
    void updateLineThrowsConflictWhenBomIsReleased() {
        BillOfMaterials released = new BillOfMaterials();
        released.setStatus(BomStatus.RELEASED);
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(released));

        assertThatThrownBy(() -> bomService.updateLine(orgId, bomId, lineId, new UpdateBomLineRequest()))
                .isInstanceOf(BomConflictException.class);
    }

    @Test
    void updateLineThrowsNotFoundWhenLineNotInBom() {
        BillOfMaterials draft = new BillOfMaterials();
        draft.setStatus(BomStatus.DRAFT);
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(draft));

        BomLine lineFromOtherBom = new BomLine();
        lineFromOtherBom.setBomId(UUID.randomUUID());
        when(bomLineRepository.findById(lineId)).thenReturn(Optional.of(lineFromOtherBom));

        assertThatThrownBy(() -> bomService.updateLine(orgId, bomId, lineId, new UpdateBomLineRequest()))
                .isInstanceOf(BomNotFoundException.class);
    }

    @Test
    void updateLineThrowsConflictWhenFindNumberAlreadyExists() {
        BillOfMaterials draft = new BillOfMaterials();
        draft.setStatus(BomStatus.DRAFT);
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(draft));

        BomLine existingLine = new BomLine();
        existingLine.setBomId(bomId);
        existingLine.setFindNumber("010");
        when(bomLineRepository.findById(lineId)).thenReturn(Optional.of(existingLine));
        when(bomLineRepository.existsByBomIdAndFindNumber(bomId, "020")).thenReturn(true);

        UpdateBomLineRequest req = new UpdateBomLineRequest();
        req.setFindNumber("020");

        assertThatThrownBy(() -> bomService.updateLine(orgId, bomId, lineId, req))
                .isInstanceOf(BomConflictException.class);
    }

    @Test
    void updateLineUpdatesQuantitySuccessfully() {
        BillOfMaterials draft = new BillOfMaterials();
        draft.setStatus(BomStatus.DRAFT);
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(draft));

        BomLine existingLine = new BomLine();
        existingLine.setBomId(bomId);
        existingLine.setFindNumber("010");
        existingLine.setQuantity(BigDecimal.ONE);
        when(bomLineRepository.findById(lineId)).thenReturn(Optional.of(existingLine));
        when(bomLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateBomLineRequest req = new UpdateBomLineRequest();
        req.setQuantity(new BigDecimal("5.0"));

        BomLine result = bomService.updateLine(orgId, bomId, lineId, req);

        assertThat(result.getQuantity()).isEqualByComparingTo(new BigDecimal("5.0"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private CreateBomLineRequest validLineRequest() {
        CreateBomLineRequest req = new CreateBomLineRequest();
        req.setComponentItemId(componentId);
        req.setQuantity(BigDecimal.ONE);
        req.setUnitOfMeasure("EA");
        req.setFindNumber("010");
        return req;
    }
}
