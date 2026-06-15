package com.mes.routing.referencedata.service;

import com.mes.routing.referencedata.api.dto.ReferenceDataDtos.RouteTypeDto;
import com.mes.routing.referencedata.api.dto.ReferenceDataDtos.WorkCentreDto;
import com.mes.routing.referencedata.domain.LabourPlanType;
import com.mes.routing.referencedata.domain.RouteType;
import com.mes.routing.referencedata.repository.LabourCodeRepository;
import com.mes.routing.referencedata.repository.LabourPlanTypeRepository;
import com.mes.routing.referencedata.repository.RouteTypeRepository;
import com.mes.routing.referencedata.repository.SignificantProcessTypeRepository;
import com.mes.routing.referencedata.repository.SupplierRepository;
import com.mes.routing.referencedata.repository.WorkCentreRepository;
import com.mes.routing.service.RoutingConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Mock WorkCentreRepository workCentres;
    @Mock LabourPlanTypeRepository labourPlanTypes;
    @Mock LabourCodeRepository labourCodes;
    @Mock RouteTypeRepository routeTypes;
    @Mock SignificantProcessTypeRepository significantProcessTypes;
    @Mock SupplierRepository suppliers;

    @InjectMocks ReferenceDataService service;

    @Test
    void createWorkCentre_duplicateCode_throwsConflict() {
        when(workCentres.existsByOrgIdAndCode(ORG, "WC-1")).thenReturn(true);

        assertThatThrownBy(() -> service.createWorkCentre(ORG,
                new WorkCentreDto(null, "WC-1", "Cell", null, true)))
                .isInstanceOf(RoutingConflictException.class);
        verify(workCentres, never()).save(any());
    }

    @Test
    void createWorkCentre_setsActiveAndPersists() {
        when(workCentres.existsByOrgIdAndCode(ORG, "WC-1")).thenReturn(false);
        when(workCentres.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WorkCentreDto dto = service.createWorkCentre(ORG, new WorkCentreDto(null, "WC-1", "Cell", "d", false));

        assertThat(dto.active()).isTrue();
        assertThat(dto.code()).isEqualTo("WC-1");
    }

    @Test
    void createRouteType_neverStandard() {
        when(routeTypes.existsByOrgIdAndCode(ORG, "NPI")).thenReturn(false);
        when(routeTypes.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RouteTypeDto dto = service.createRouteType(ORG, new RouteTypeDto(null, "NPI", "NPI", true, true, true));

        assertThat(dto.isStandard()).isFalse();
        assertThat(dto.seeded()).isFalse();
    }

    @Test
    void deleteLabourPlanType_seeded_throwsConflict() {
        LabourPlanType seeded = new LabourPlanType();
        seeded.setOrgId(ORG);
        seeded.setCode("MACHINE");
        seeded.setSeeded(true);
        UUID id = UUID.randomUUID();
        when(labourPlanTypes.findByOrgIdAndId(ORG, id)).thenReturn(Optional.of(seeded));

        assertThatThrownBy(() -> service.deleteLabourPlanType(ORG, id))
                .isInstanceOf(RoutingConflictException.class);
        verify(labourPlanTypes, never()).delete(any());
    }

    @Test
    void deleteLabourPlanType_inUse_throwsConflict() {
        LabourPlanType inUse = new LabourPlanType();
        inUse.setOrgId(ORG);
        inUse.setCode("SUBCON");
        inUse.setSeeded(false);
        UUID id = UUID.randomUUID();
        when(labourPlanTypes.findByOrgIdAndId(ORG, id)).thenReturn(Optional.of(inUse));
        when(labourCodes.existsByOrgIdAndLabourPlanTypeId(ORG, id)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteLabourPlanType(ORG, id))
                .isInstanceOf(RoutingConflictException.class);
        verify(labourPlanTypes, never()).delete(any());
    }

    @Test
    void deleteRouteType_standard_throwsConflict() {
        RouteType standard = new RouteType();
        standard.setOrgId(ORG);
        standard.setCode("STANDARD");
        standard.setStandard(true);
        standard.setSeeded(true);
        UUID id = UUID.randomUUID();
        when(routeTypes.findByOrgIdAndId(ORG, id)).thenReturn(Optional.of(standard));

        assertThatThrownBy(() -> service.deleteRouteType(ORG, id))
                .isInstanceOf(RoutingConflictException.class);
        verify(routeTypes, never()).delete(any());
    }
}
