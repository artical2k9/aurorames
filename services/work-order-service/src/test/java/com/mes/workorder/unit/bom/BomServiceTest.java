package com.mes.workorder.unit.bom;

import com.mes.workorder.bom.api.dto.CreateBomLineRequest;
import com.mes.workorder.bom.domain.BillOfMaterials;
import com.mes.workorder.bom.domain.BomStatus;
import com.mes.workorder.bom.repository.BomLineRepository;
import com.mes.workorder.bom.repository.BomRepository;
import com.mes.workorder.bom.service.BomConflictException;
import com.mes.workorder.bom.service.BomService;
import com.mes.workorder.itemmaster.repository.ItemMasterRepository;
import com.mes.workorder.kafka.BomEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

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

    @InjectMocks
    BomService bomService;

    UUID orgId = UUID.randomUUID();
    UUID bomId = UUID.randomUUID();
    UUID componentId = UUID.randomUUID();

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
