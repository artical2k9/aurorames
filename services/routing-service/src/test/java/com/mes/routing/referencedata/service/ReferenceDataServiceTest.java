package com.mes.routing.referencedata.service;

import com.mes.routing.referencedata.api.dto.ReferenceDataDtos.LabourCodeDto;
import com.mes.routing.referencedata.api.dto.ReferenceDataDtos.RouteTypeDto;
import com.mes.routing.referencedata.api.dto.ReferenceDataDtos.SignificantProcessTypeDto;
import com.mes.routing.referencedata.api.dto.ReferenceDataDtos.SupplierDto;
import com.mes.routing.referencedata.api.dto.ReferenceDataDtos.WorkCentreDto;
import com.mes.routing.referencedata.domain.LabourCode;
import com.mes.routing.referencedata.domain.LabourPlanType;
import com.mes.routing.referencedata.domain.RouteType;
import com.mes.routing.referencedata.domain.SignificantProcessType;
import com.mes.routing.referencedata.domain.Supplier;
import com.mes.routing.referencedata.domain.WorkCentre;
import com.mes.routing.referencedata.repository.LabourCodeRepository;
import com.mes.routing.referencedata.repository.LabourPlanTypeRepository;
import com.mes.routing.referencedata.repository.RouteTypeRepository;
import com.mes.routing.referencedata.repository.SignificantProcessTypeRepository;
import com.mes.routing.referencedata.repository.SupplierRepository;
import com.mes.routing.referencedata.repository.WorkCentreRepository;
import com.mes.routing.service.RoutingConflictException;
import com.mes.routing.service.RoutingNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceDataServiceTest {

    static final UUID ORG = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID ID = UUID.randomUUID();

    @Mock WorkCentreRepository workCentres;
    @Mock LabourPlanTypeRepository labourPlanTypes;
    @Mock LabourCodeRepository labourCodes;
    @Mock RouteTypeRepository routeTypes;
    @Mock SignificantProcessTypeRepository significantProcessTypes;
    @Mock SupplierRepository suppliers;

    @InjectMocks ReferenceDataService service;

    private static <T> T echo(org.mockito.invocation.InvocationOnMock inv) {
        return inv.getArgument(0);
    }

    // ── Work centres ─────────────────────────────────────────────────────────

    @Test
    void workCentre_listCreateUpdateDelete() {
        WorkCentre e = new WorkCentre();
        e.setOrgId(ORG);
        e.setCode("WC");
        e.setName("Cell");
        when(workCentres.findByOrgIdOrderByCode(ORG)).thenReturn(List.of(e));
        assertThat(service.listWorkCentres(ORG)).hasSize(1);

        when(workCentres.existsByOrgIdAndCode(ORG, "WC")).thenReturn(false);
        when(workCentres.save(any())).thenAnswer(ReferenceDataServiceTest::echo);
        assertThat(service.createWorkCentre(ORG, new WorkCentreDto(null, "WC", "Cell", "d", true)).code())
                .isEqualTo("WC");

        when(workCentres.findByOrgIdAndId(ORG, ID)).thenReturn(Optional.of(e));
        assertThat(service.updateWorkCentre(ORG, ID, new WorkCentreDto(ID, "WC", "New", "d", false)).name())
                .isEqualTo("New");

        service.deleteWorkCentre(ORG, ID);
        verify(workCentres).delete(e);
    }

    @Test
    void workCentre_createDuplicate_conflict() {
        when(workCentres.existsByOrgIdAndCode(ORG, "WC")).thenReturn(true);
        assertThatThrownBy(() -> service.createWorkCentre(ORG, new WorkCentreDto(null, "WC", "n", null, true)))
                .isInstanceOf(RoutingConflictException.class);
    }

    @Test
    void workCentre_updateOrDeleteUnknown_notFound() {
        when(workCentres.findByOrgIdAndId(ORG, ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteWorkCentre(ORG, ID))
                .isInstanceOf(RoutingNotFoundException.class);
    }

    // ── Labour plan types ──────────────────────────────────────────────────────

    @Test
    void labourPlanType_listCreateUpdate() {
        LabourPlanType e = new LabourPlanType();
        e.setOrgId(ORG);
        e.setCode("M");
        e.setName("Machine");
        when(labourPlanTypes.findByOrgIdOrderByCode(ORG)).thenReturn(List.of(e));
        assertThat(service.listLabourPlanTypes(ORG)).hasSize(1);

        when(labourPlanTypes.existsByOrgIdAndCode(ORG, "SUB")).thenReturn(false);
        when(labourPlanTypes.save(any())).thenAnswer(ReferenceDataServiceTest::echo);
        assertThat(service.createLabourPlanType(ORG,
                new com.mes.routing.referencedata.api.dto.ReferenceDataDtos.LabourPlanTypeDto(
                        null, "SUB", "Subcontract", true, true)).seeded()).isFalse();

        when(labourPlanTypes.findByOrgIdAndId(ORG, ID)).thenReturn(Optional.of(e));
        assertThat(service.updateLabourPlanType(ORG, ID,
                new com.mes.routing.referencedata.api.dto.ReferenceDataDtos.LabourPlanTypeDto(
                        ID, "M", "Machine 2", false, false)).name()).isEqualTo("Machine 2");
    }

    @Test
    void labourPlanType_deleteNotInUse_deletes() {
        LabourPlanType e = new LabourPlanType();
        e.setSeeded(false);
        when(labourPlanTypes.findByOrgIdAndId(ORG, ID)).thenReturn(Optional.of(e));
        when(labourCodes.existsByOrgIdAndLabourPlanTypeId(ORG, ID)).thenReturn(false);
        service.deleteLabourPlanType(ORG, ID);
        verify(labourPlanTypes).delete(e);
    }

    @Test
    void labourPlanType_deleteSeeded_conflict() {
        LabourPlanType e = new LabourPlanType();
        e.setSeeded(true);
        e.setCode("MACHINE");
        when(labourPlanTypes.findByOrgIdAndId(ORG, ID)).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> service.deleteLabourPlanType(ORG, ID))
                .isInstanceOf(RoutingConflictException.class);
        verify(labourPlanTypes, never()).delete(any());
    }

    @Test
    void labourPlanType_deleteInUse_conflict() {
        LabourPlanType e = new LabourPlanType();
        e.setSeeded(false);
        e.setCode("SUB");
        when(labourPlanTypes.findByOrgIdAndId(ORG, ID)).thenReturn(Optional.of(e));
        when(labourCodes.existsByOrgIdAndLabourPlanTypeId(ORG, ID)).thenReturn(true);
        assertThatThrownBy(() -> service.deleteLabourPlanType(ORG, ID))
                .isInstanceOf(RoutingConflictException.class);
    }

    // ── Labour codes ───────────────────────────────────────────────────────────

    @Test
    void labourCode_listCreateUpdateDelete() {
        LabourCode e = new LabourCode();
        e.setOrgId(ORG);
        e.setCode("LC");
        e.setName("Welder");
        when(labourCodes.findByOrgIdOrderByCode(ORG)).thenReturn(List.of(e));
        assertThat(service.listLabourCodes(ORG)).hasSize(1);

        when(labourCodes.existsByOrgIdAndCode(ORG, "LC")).thenReturn(false);
        when(labourCodes.save(any())).thenAnswer(ReferenceDataServiceTest::echo);
        assertThat(service.createLabourCode(ORG, new LabourCodeDto(null, "LC", "Welder", null, true)).code())
                .isEqualTo("LC");

        when(labourCodes.findByOrgIdAndId(ORG, ID)).thenReturn(Optional.of(e));
        assertThat(service.updateLabourCode(ORG, ID, new LabourCodeDto(ID, "LC", "Brazer", null, false)).name())
                .isEqualTo("Brazer");

        service.deleteLabourCode(ORG, ID);
        verify(labourCodes).delete(e);
    }

    @Test
    void labourCode_createWithUnknownPlanType_notFound() {
        UUID planTypeId = UUID.randomUUID();
        when(labourCodes.existsByOrgIdAndCode(ORG, "LC")).thenReturn(false);
        when(labourPlanTypes.findByOrgIdAndId(ORG, planTypeId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createLabourCode(ORG,
                new LabourCodeDto(null, "LC", "Welder", planTypeId, true)))
                .isInstanceOf(RoutingNotFoundException.class);
    }

    // ── Route types ────────────────────────────────────────────────────────────

    @Test
    void routeType_listCreateNeverStandardUpdateDelete() {
        RouteType e = new RouteType();
        e.setOrgId(ORG);
        e.setCode("NPI");
        e.setName("NPI");
        when(routeTypes.findByOrgIdOrderByCode(ORG)).thenReturn(List.of(e));
        assertThat(service.listRouteTypes(ORG)).hasSize(1);

        when(routeTypes.existsByOrgIdAndCode(ORG, "NPI")).thenReturn(false);
        when(routeTypes.save(any())).thenAnswer(ReferenceDataServiceTest::echo);
        RouteTypeDto created = service.createRouteType(ORG, new RouteTypeDto(null, "NPI", "NPI", true, true, true));
        assertThat(created.isStandard()).isFalse();
        assertThat(created.seeded()).isFalse();

        when(routeTypes.findByOrgIdAndId(ORG, ID)).thenReturn(Optional.of(e));
        assertThat(service.updateRouteType(ORG, ID, new RouteTypeDto(ID, "NPI", "NPI v2", false, false, true))
                .name()).isEqualTo("NPI v2");

        service.deleteRouteType(ORG, ID);
        verify(routeTypes).delete(e);
    }

    @Test
    void routeType_deleteStandard_conflict() {
        RouteType e = new RouteType();
        e.setStandard(true);
        e.setSeeded(true);
        e.setCode("STANDARD");
        when(routeTypes.findByOrgIdAndId(ORG, ID)).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> service.deleteRouteType(ORG, ID))
                .isInstanceOf(RoutingConflictException.class);
        verify(routeTypes, never()).delete(any());
    }

    // ── Significant-process types ────────────────────────────────────────────────

    @Test
    void significantProcessType_listCreateUpdateDelete() {
        SignificantProcessType e = new SignificantProcessType();
        e.setOrgId(ORG);
        e.setCode("BRAZE");
        e.setName("Brazing");
        e.setRequiredApproverRole("SME");
        when(significantProcessTypes.findByOrgIdOrderByCode(ORG)).thenReturn(List.of(e));
        assertThat(service.listSignificantProcessTypes(ORG)).hasSize(1);

        when(significantProcessTypes.existsByOrgIdAndCode(ORG, "BRAZE")).thenReturn(false);
        when(significantProcessTypes.save(any())).thenAnswer(ReferenceDataServiceTest::echo);
        assertThat(service.createSignificantProcessType(ORG,
                new SignificantProcessTypeDto(null, "BRAZE", "Brazing", "SME", true)).requiredApproverRole())
                .isEqualTo("SME");

        when(significantProcessTypes.findByOrgIdAndId(ORG, ID)).thenReturn(Optional.of(e));
        assertThat(service.updateSignificantProcessType(ORG, ID,
                new SignificantProcessTypeDto(ID, "BRAZE", "Brazing", "SME2", false)).requiredApproverRole())
                .isEqualTo("SME2");

        service.deleteSignificantProcessType(ORG, ID);
        verify(significantProcessTypes).delete(e);
    }

    // ── Suppliers ────────────────────────────────────────────────────────────────

    @Test
    void supplier_listCreateUpdateDelete() {
        Supplier e = new Supplier();
        e.setOrgId(ORG);
        e.setCode("V1");
        e.setName("Vendor");
        when(suppliers.findByOrgIdOrderByCode(ORG)).thenReturn(List.of(e));
        assertThat(service.listSuppliers(ORG)).hasSize(1);

        when(suppliers.existsByOrgIdAndCode(ORG, "V1")).thenReturn(false);
        when(suppliers.save(any())).thenAnswer(ReferenceDataServiceTest::echo);
        assertThat(service.createSupplier(ORG, new SupplierDto(null, "V1", "Vendor", true)).code())
                .isEqualTo("V1");

        when(suppliers.findByOrgIdAndId(ORG, ID)).thenReturn(Optional.of(e));
        assertThat(service.updateSupplier(ORG, ID, new SupplierDto(ID, "V1", "Vendor 2", false)).name())
                .isEqualTo("Vendor 2");

        service.deleteSupplier(ORG, ID);
        verify(suppliers).delete(e);
    }
}
