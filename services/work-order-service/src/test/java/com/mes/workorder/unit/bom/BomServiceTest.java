package com.mes.workorder.unit.bom;

import com.mes.workorder.bom.api.dto.CreateBomLineRequest;
import com.mes.workorder.bom.api.dto.CreateBomRequest;
import com.mes.workorder.bom.api.dto.UpdateBomLineRequest;
import com.mes.workorder.bom.domain.BillOfMaterials;
import com.mes.workorder.bom.domain.BomLine;
import com.mes.workorder.bom.domain.BomStatus;
import com.mes.workorder.bom.domain.EffectivityMethod;
import com.mes.workorder.bom.repository.BomLineRepository;
import com.mes.workorder.bom.repository.BomRepository;
import com.mes.workorder.bom.service.BomConflictException;
import com.mes.workorder.bom.service.BomNotFoundException;
import com.mes.workorder.bom.service.BomService;
import com.mes.workorder.bom.service.BomValidationException;
import com.mes.workorder.bom.service.EffectivityValidator;
import com.mes.workorder.eco.service.EcoService;
import com.mes.workorder.itemmaster.domain.CounterfeitRiskLevel;
import com.mes.workorder.itemmaster.domain.ItemMaster;
import com.mes.workorder.itemmaster.repository.ItemMasterRepository;
import com.mes.workorder.kafka.BomEventPublisher;
import com.mes.workorder.kafka.ItemMasterEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    UUID parentItemId = UUID.randomUUID();

    // ── createBom ─────────────────────────────────────────────────────────────

    @Test
    void createBomThrowsValidationWhenParentItemNotFound() {
        when(itemMasterRepository.existsByOrgIdAndId(orgId, parentItemId)).thenReturn(false);

        assertThatThrownBy(() -> bomService.createBom(orgId, createBomRequest()))
                .isInstanceOf(BomValidationException.class);
    }

    @Test
    void createBomThrowsConflictWhenRevisionAlreadyExists() {
        when(itemMasterRepository.existsByOrgIdAndId(orgId, parentItemId)).thenReturn(true);
        when(bomRepository.existsByOrgIdAndParentItemIdAndBomRevision(orgId, parentItemId, "REV-A"))
                .thenReturn(true);

        assertThatThrownBy(() -> bomService.createBom(orgId, createBomRequest()))
                .isInstanceOf(BomConflictException.class);
    }

    @Test
    void createBomReturnsSavedBom() {
        when(itemMasterRepository.existsByOrgIdAndId(orgId, parentItemId)).thenReturn(true);
        when(bomRepository.existsByOrgIdAndParentItemIdAndBomRevision(orgId, parentItemId, "REV-A"))
                .thenReturn(false);
        BillOfMaterials saved = new BillOfMaterials();
        when(bomRepository.save(any())).thenReturn(saved);

        BillOfMaterials result = bomService.createBom(orgId, createBomRequest());

        assertThat(result).isSameAs(saved);
        verify(bomRepository).save(any());
    }

    // ── getBom ────────────────────────────────────────────────────────────────

    @Test
    void getBomThrowsNotFoundWhenMissing() {
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bomService.getBom(orgId, bomId))
                .isInstanceOf(BomNotFoundException.class);
    }

    @Test
    void getBomReturnsBomWhenFound() {
        BillOfMaterials bom = new BillOfMaterials();
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(bom));

        assertThat(bomService.getBom(orgId, bomId)).isSameAs(bom);
    }

    // ── listLines ─────────────────────────────────────────────────────────────

    @Test
    void listLinesThrowsNotFoundWhenBomMissing() {
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bomService.listLines(orgId, bomId))
                .isInstanceOf(BomNotFoundException.class);
    }

    @Test
    void listLinesReturnsAllLines() {
        BillOfMaterials bom = new BillOfMaterials();
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(bom));
        BomLine line = new BomLine();
        when(bomLineRepository.findAllByBomId(bomId)).thenReturn(List.of(line));

        assertThat(bomService.listLines(orgId, bomId)).containsExactly(line);
    }

    // ── releaseBom ────────────────────────────────────────────────────────────

    @Test
    void releaseBomThrowsNotFoundWhenMissing() {
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bomService.releaseBom(orgId, bomId))
                .isInstanceOf(BomNotFoundException.class);
    }

    @Test
    void releaseBomThrowsConflictWhenNotDraft() {
        BillOfMaterials released = new BillOfMaterials();
        released.setStatus(BomStatus.RELEASED);
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(released));

        assertThatThrownBy(() -> bomService.releaseBom(orgId, bomId))
                .isInstanceOf(BomConflictException.class);
    }

    @Test
    void releaseBomPublishesEventAndReturnsReleasedBom() {
        BillOfMaterials draft = new BillOfMaterials();
        draft.setStatus(BomStatus.DRAFT);
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(draft));
        when(bomRepository.save(draft)).thenReturn(draft);

        BillOfMaterials result = bomService.releaseBom(orgId, bomId);

        assertThat(result.getStatus()).isEqualTo(BomStatus.RELEASED);
        verify(bomEventPublisher).publishReleased(draft);
        verify(ecoService, never()).addOutputBom(any(), any());
    }

    @Test
    void releaseBomWithEcoIdCallsEcoService() {
        UUID ecoId = UUID.randomUUID();
        BillOfMaterials draft = new BillOfMaterials();
        draft.setStatus(BomStatus.DRAFT);
        draft.setEcoId(ecoId);
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(draft));
        when(bomRepository.save(draft)).thenReturn(draft);

        bomService.releaseBom(orgId, bomId);

        verify(ecoService).addOutputBom(eq(ecoId), any());
    }

    // ── addLine ───────────────────────────────────────────────────────────────

    @Test
    void addLineThrowsConflictWhenBomIsReleased() {
        BillOfMaterials released = new BillOfMaterials();
        released.setStatus(BomStatus.RELEASED);
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(released));

        assertThatThrownBy(() -> bomService.addLine(orgId, bomId, validLineRequest()))
                .isInstanceOf(BomConflictException.class);
    }

    @Test
    void addLineDuplicateFindNumberThrowsConflict() {
        BillOfMaterials draft = draftBom();
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(draft));
        when(itemMasterRepository.existsByOrgIdAndId(orgId, componentId)).thenReturn(true);
        when(bomLineRepository.existsByBomIdAndFindNumber(bomId, "010")).thenReturn(true);

        assertThatThrownBy(() -> bomService.addLine(orgId, bomId, validLineRequest()))
                .isInstanceOf(BomConflictException.class);
    }

    @Test
    void addLineWithDateEffectivityRequiresEffectiveFromDate() {
        BillOfMaterials draft = draftBom();
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(draft));
        when(itemMasterRepository.existsByOrgIdAndId(orgId, componentId)).thenReturn(true);

        CreateBomLineRequest req = validLineRequest();
        req.setEffectivityMethod(EffectivityMethod.DATE);
        req.setEffectiveFromDate(null);

        assertThatThrownBy(() -> bomService.addLine(orgId, bomId, req))
                .isInstanceOf(BomValidationException.class)
                .hasMessageContaining("effectiveFromDate");
    }

    @Test
    void addLineWithDateEffectivityCallsEffectivityValidator() {
        BillOfMaterials draft = draftBom();
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(draft));
        when(itemMasterRepository.existsByOrgIdAndId(orgId, componentId)).thenReturn(true);
        when(bomRepository.hasAncestorCycle(bomId, componentId)).thenReturn(false);
        when(bomLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateBomLineRequest req = validLineRequest();
        req.setEffectivityMethod(EffectivityMethod.DATE);
        req.setEffectiveFromDate(LocalDate.of(2025, 1, 1));

        bomService.addLine(orgId, bomId, req);

        verify(effectivityValidator).validateNewLine(eq(bomId), eq(req));
    }

    @Test
    void addLineWithUnitEffectivityRequiresEffectiveFromUnit() {
        BillOfMaterials draft = draftBom();
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(draft));
        when(itemMasterRepository.existsByOrgIdAndId(orgId, componentId)).thenReturn(true);

        CreateBomLineRequest req = validLineRequest();
        req.setEffectivityMethod(EffectivityMethod.UNIT);
        req.setEffectiveFromUnit(null);

        assertThatThrownBy(() -> bomService.addLine(orgId, bomId, req))
                .isInstanceOf(BomValidationException.class)
                .hasMessageContaining("effectiveFromUnit");
    }

    @Test
    void addLineThrowsValidationWhenCircularReferenceDetected() {
        BillOfMaterials draft = draftBom();
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(draft));
        when(itemMasterRepository.existsByOrgIdAndId(orgId, componentId)).thenReturn(true);
        when(bomLineRepository.existsByBomIdAndFindNumber(bomId, "010")).thenReturn(false);
        when(bomRepository.hasAncestorCycle(bomId, componentId)).thenReturn(true);

        assertThatThrownBy(() -> bomService.addLine(orgId, bomId, validLineRequest()))
                .isInstanceOf(BomValidationException.class)
                .hasMessageContaining("circular");
    }

    @Test
    void addLineCallsCircularCheckBeforeInsert() {
        BillOfMaterials draft = draftBom();
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(draft));
        when(itemMasterRepository.existsByOrgIdAndId(orgId, componentId)).thenReturn(true);
        when(bomLineRepository.existsByBomIdAndFindNumber(bomId, "010")).thenReturn(false);
        when(bomRepository.hasAncestorCycle(bomId, componentId)).thenReturn(false);
        when(bomLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        bomService.addLine(orgId, bomId, validLineRequest());

        verify(bomRepository).hasAncestorCycle(bomId, componentId);
    }

    @Test
    void addLinePublishesAS5553EventForHighRiskComponent() {
        BillOfMaterials draft = draftBom();
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(draft));
        when(itemMasterRepository.existsByOrgIdAndId(orgId, componentId)).thenReturn(true);
        when(bomLineRepository.existsByBomIdAndFindNumber(bomId, "010")).thenReturn(false);
        when(bomRepository.hasAncestorCycle(bomId, componentId)).thenReturn(false);
        when(bomLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ItemMaster highRiskItem = new ItemMaster();
        highRiskItem.setCounterfeitRiskLevel(CounterfeitRiskLevel.HIGH);
        when(itemMasterRepository.findByOrgIdAndId(orgId, componentId))
                .thenReturn(Optional.of(highRiskItem));

        bomService.addLine(orgId, bomId, validLineRequest());

        verify(itemMasterEventPublisher).publishAs5553RiskAdded(highRiskItem);
    }

    // ── updateLine ────────────────────────────────────────────────────────────

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
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(draftBom()));
        BomLine lineFromOtherBom = new BomLine();
        lineFromOtherBom.setBomId(UUID.randomUUID());
        when(bomLineRepository.findById(lineId)).thenReturn(Optional.of(lineFromOtherBom));

        assertThatThrownBy(() -> bomService.updateLine(orgId, bomId, lineId, new UpdateBomLineRequest()))
                .isInstanceOf(BomNotFoundException.class);
    }

    @Test
    void updateLineThrowsConflictWhenFindNumberAlreadyExists() {
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(draftBom()));
        BomLine existingLine = lineInBom("010");
        when(bomLineRepository.findById(lineId)).thenReturn(Optional.of(existingLine));
        when(bomLineRepository.existsByBomIdAndFindNumber(bomId, "020")).thenReturn(true);

        UpdateBomLineRequest req = new UpdateBomLineRequest();
        req.setFindNumber("020");

        assertThatThrownBy(() -> bomService.updateLine(orgId, bomId, lineId, req))
                .isInstanceOf(BomConflictException.class);
    }

    @Test
    void updateLineUpdatesQuantitySuccessfully() {
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(draftBom()));
        BomLine existingLine = lineInBom("010");
        existingLine.setQuantity(BigDecimal.ONE);
        when(bomLineRepository.findById(lineId)).thenReturn(Optional.of(existingLine));
        when(bomLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateBomLineRequest req = new UpdateBomLineRequest();
        req.setQuantity(new BigDecimal("5.0"));

        BomLine result = bomService.updateLine(orgId, bomId, lineId, req);

        assertThat(result.getQuantity()).isEqualByComparingTo(new BigDecimal("5.0"));
    }

    @Test
    void updateLineAppliesUnitOfMeasureAndReferenceDesignators() {
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(draftBom()));
        BomLine existingLine = lineInBom("010");
        when(bomLineRepository.findById(lineId)).thenReturn(Optional.of(existingLine));
        when(bomLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateBomLineRequest req = new UpdateBomLineRequest();
        req.setUnitOfMeasure("KG");
        req.setReferenceDesignators("R1, R2");

        BomLine result = bomService.updateLine(orgId, bomId, lineId, req);

        assertThat(result.getUnitOfMeasure()).isEqualTo("KG");
        assertThat(result.getReferenceDesignators()).isEqualTo("R1, R2");
    }

    @Test
    void updateLineSetsNewFindNumberWhenNotDuplicated() {
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(draftBom()));
        BomLine existingLine = lineInBom("010");
        when(bomLineRepository.findById(lineId)).thenReturn(Optional.of(existingLine));
        when(bomLineRepository.existsByBomIdAndFindNumber(bomId, "020")).thenReturn(false);
        when(bomLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateBomLineRequest req = new UpdateBomLineRequest();
        req.setFindNumber("020");

        BomLine result = bomService.updateLine(orgId, bomId, lineId, req);

        assertThat(result.getFindNumber()).isEqualTo("020");
    }

    @Test
    void updateLineWithEffectivityChangesCallsValidator() {
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(draftBom()));
        BomLine existingLine = lineInBom("010");
        existingLine.setEffectivityMethod(EffectivityMethod.DATE);
        when(bomLineRepository.findById(lineId)).thenReturn(Optional.of(existingLine));
        when(bomLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateBomLineRequest req = new UpdateBomLineRequest();
        req.setEffectivityMethod(EffectivityMethod.DATE);
        req.setEffectiveFromDate(LocalDate.of(2025, 1, 1));
        req.setEffectiveToDate(LocalDate.of(2025, 12, 31));
        req.setEffectiveFromUnit("100");
        req.setEffectiveToUnit("200");

        BomLine result = bomService.updateLine(orgId, bomId, lineId, req);

        verify(effectivityValidator).validateUpdateLine(
                eq(bomId), eq("010"), any(), any(), any(), eq(lineId));
        assertThat(result.getEffectiveFromUnit()).isEqualTo("100");
        assertThat(result.getEffectiveToUnit()).isEqualTo("200");
    }

    // ── listByItem ────────────────────────────────────────────────────────────

    @Test
    void listByItemReturnsBomListFromRepo() {
        BillOfMaterials bom = draftBom();
        when(bomRepository.findAllByOrgIdAndParentItemId(orgId, parentItemId))
                .thenReturn(List.of(bom));

        List<BillOfMaterials> result = bomService.listByItem(orgId, parentItemId);

        assertThat(result).containsExactly(bom);
    }

    // ── deleteLine ────────────────────────────────────────────────────────────

    @Test
    void deleteLineRemovesLineFromRepo() {
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(draftBom()));
        BomLine line = lineInBom("010");
        when(bomLineRepository.findById(lineId)).thenReturn(Optional.of(line));

        bomService.deleteLine(orgId, bomId, lineId);

        verify(bomLineRepository).delete(line);
    }

    @Test
    void deleteLineThrowsNotFoundWhenBomMissing() {
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bomService.deleteLine(orgId, bomId, lineId))
                .isInstanceOf(BomNotFoundException.class);
    }

    @Test
    void deleteLineThrowsConflictWhenBomNotDraft() {
        BillOfMaterials released = new BillOfMaterials();
        released.setStatus(BomStatus.RELEASED);
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(released));

        assertThatThrownBy(() -> bomService.deleteLine(orgId, bomId, lineId))
                .isInstanceOf(BomConflictException.class);
    }

    @Test
    void deleteLineThrowsNotFoundWhenLineNotInBom() {
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(draftBom()));
        BomLine line = lineInBom("010");
        line.setBomId(UUID.randomUUID()); // different BOM
        when(bomLineRepository.findById(lineId)).thenReturn(Optional.of(line));

        assertThatThrownBy(() -> bomService.deleteLine(orgId, bomId, lineId))
                .isInstanceOf(BomNotFoundException.class);
    }

    // ── patchHeader ───────────────────────────────────────────────────────────

    @Test
    void patchHeaderUpdatesDescriptionAndSaves() {
        BillOfMaterials bom = draftBom();
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(bom));
        when(bomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        com.mes.workorder.bom.api.dto.PatchBomHeaderRequest req =
                new com.mes.workorder.bom.api.dto.PatchBomHeaderRequest();
        req.setDescription("Updated");
        req.setReasonForRevision("Design change");
        req.setProductionLine("LINE-A");
        req.setBomType("MANUFACTURING");
        req.setEffectivityType("DATE");

        BillOfMaterials result = bomService.patchHeader(orgId, bomId, req);

        assertThat(result.getDescription()).isEqualTo("Updated");
        assertThat(result.getReasonForRevision()).isEqualTo("Design change");
        assertThat(result.getProductionLine()).isEqualTo("LINE-A");
        assertThat(result.getBomType()).isEqualTo("MANUFACTURING");
        assertThat(result.getEffectivityType()).isEqualTo("DATE");
        verify(bomRepository).save(bom);
    }

    @Test
    void patchHeaderIgnoresNullFields() {
        BillOfMaterials bom = draftBom();
        bom.setDescription("Original");
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.of(bom));
        when(bomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        com.mes.workorder.bom.api.dto.PatchBomHeaderRequest req =
                new com.mes.workorder.bom.api.dto.PatchBomHeaderRequest();
        // all fields null

        BillOfMaterials result = bomService.patchHeader(orgId, bomId, req);

        assertThat(result.getDescription()).isEqualTo("Original");
    }

    @Test
    void patchHeaderThrowsNotFoundWhenBomMissing() {
        when(bomRepository.findByOrgIdAndId(orgId, bomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bomService.patchHeader(orgId, bomId,
                new com.mes.workorder.bom.api.dto.PatchBomHeaderRequest()))
                .isInstanceOf(BomNotFoundException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private BillOfMaterials draftBom() {
        BillOfMaterials bom = new BillOfMaterials();
        bom.setStatus(BomStatus.DRAFT);
        return bom;
    }

    private BomLine lineInBom(String findNumber) {
        BomLine line = new BomLine();
        line.setBomId(bomId);
        line.setFindNumber(findNumber);
        return line;
    }

    private CreateBomRequest createBomRequest() {
        CreateBomRequest req = new CreateBomRequest();
        req.setParentItemId(parentItemId);
        req.setBomRevision("REV-A");
        return req;
    }

    private CreateBomLineRequest validLineRequest() {
        CreateBomLineRequest req = new CreateBomLineRequest();
        req.setComponentItemId(componentId);
        req.setQuantity(BigDecimal.ONE);
        req.setUnitOfMeasure("EA");
        req.setFindNumber("010");
        return req;
    }
}
