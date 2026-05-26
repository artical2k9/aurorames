package com.mes.workorder.unit.itemmaster;

import com.mes.workorder.itemmaster.api.dto.CreateItemMasterRequest;
import com.mes.workorder.itemmaster.domain.Classification;
import com.mes.workorder.itemmaster.domain.ItemMaster;
import com.mes.workorder.itemmaster.domain.MakeBuyCode;
import com.mes.workorder.itemmaster.domain.TraceabilityMethod;
import com.mes.workorder.itemmaster.repository.ItemMasterRepository;
import com.mes.workorder.itemmaster.service.ItemMasterConflictException;
import com.mes.workorder.itemmaster.service.ItemMasterService;
import com.mes.workorder.itemmaster.service.ItemMasterValidationException;
import com.mes.workorder.kafka.ItemMasterEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemMasterServiceTest {

    @Mock ItemMasterRepository repository;
    @Mock ItemMasterEventPublisher eventPublisher;

    @InjectMocks
    ItemMasterService service;

    UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("engineer-user", null, List.of()));
    }

    @Test
    void createThrowsConflictWhenPartNumberRevisionAlreadyExists() {
        when(repository.existsByOrgIdAndPartNumberAndRevision(any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.create(orgId, "BRKT-001", "A", validRequest()))
                .isInstanceOf(ItemMasterConflictException.class);
    }

    @Test
    void createThrowsValidationExceptionWhenShelfLifeControlledWithoutDays() {
        when(repository.existsByOrgIdAndPartNumberAndRevision(any(), any(), any())).thenReturn(false);

        CreateItemMasterRequest req = validRequest();
        req.setShelfLifeControlled(true);
        req.setShelfLifeDays(null);

        assertThatThrownBy(() -> service.create(orgId, "BRKT-001", "A", req))
                .isInstanceOf(ItemMasterValidationException.class);
    }

    @Test
    void createPopulatesAuditFieldsFromSecurityContext() {
        when(repository.existsByOrgIdAndPartNumberAndRevision(any(), any(), any())).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ItemMaster saved = service.create(orgId, "BRKT-001", "A", validRequest());

        assertThat(saved.getCreatedBy()).isEqualTo("engineer-user");
        assertThat(saved.getModifiedBy()).isEqualTo("engineer-user");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private CreateItemMasterRequest validRequest() {
        var req = new CreateItemMasterRequest();
        req.setDescription("Aluminium bracket");
        req.setUnitOfMeasure("EA");
        req.setClassification(Classification.FABRICATED);
        req.setMakeBuyCode(MakeBuyCode.MAKE);
        req.setTraceabilityMethod(TraceabilityMethod.SERIAL);
        return req;
    }
}
