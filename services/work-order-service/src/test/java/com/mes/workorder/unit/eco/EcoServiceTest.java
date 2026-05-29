package com.mes.workorder.unit.eco;

import com.mes.workorder.eco.domain.EngineeringChangeOrder;
import com.mes.workorder.eco.domain.EcoStatus;
import com.mes.workorder.eco.repository.EcoRepository;
import com.mes.workorder.eco.service.EcoConflictException;
import com.mes.workorder.eco.service.EcoService;
import com.mes.workorder.kafka.EcoEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EcoServiceTest {

    @Mock EcoRepository ecoRepository;
    @Mock EcoEventPublisher ecoEventPublisher;

    @InjectMocks
    EcoService ecoService;

    UUID orgId = UUID.randomUUID();
    UUID ecoId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();

    @Test
    void createQueriesOpenEcosForEachAffectedItem() {
        when(ecoRepository.countOpenEcosForItemId(orgId, itemId)).thenReturn(1L);

        var req = createRequest("Test ECO", List.of(itemId));
        when(ecoRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        var result = ecoService.create(orgId, req, "test-user");

        verify(ecoRepository).countOpenEcosForItemId(orgId, itemId);
        org.assertj.core.api.Assertions.assertThat(result.isConcurrentEcoWarning()).isTrue();
    }

    @Test
    void approveApprovedEcoThrowsConflict() {
        EngineeringChangeOrder eco = new EngineeringChangeOrder();
        eco.setStatus(EcoStatus.APPROVED);
        when(ecoRepository.findByOrgIdAndId(orgId, ecoId)).thenReturn(Optional.of(eco));

        assertThatThrownBy(() -> ecoService.approve(orgId, ecoId, "approver"))
                .isInstanceOf(EcoConflictException.class);
    }

    @Test
    void approveImplementedEcoThrowsConflict() {
        EngineeringChangeOrder eco = new EngineeringChangeOrder();
        eco.setStatus(EcoStatus.IMPLEMENTED);
        when(ecoRepository.findByOrgIdAndId(orgId, ecoId)).thenReturn(Optional.of(eco));

        assertThatThrownBy(() -> ecoService.approve(orgId, ecoId, "approver"))
                .isInstanceOf(EcoConflictException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private com.mes.workorder.eco.api.dto.CreateEcoRequest createRequest(String title, List<UUID> itemIds) {
        com.mes.workorder.eco.api.dto.CreateEcoRequest req = new com.mes.workorder.eco.api.dto.CreateEcoRequest();
        req.setTitle(title);
        req.setAffectedItemIds(itemIds);
        return req;
    }
}
