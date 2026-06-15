package com.mes.routing.referencedata.api;

import com.mes.routing.referencedata.api.dto.ReferenceDataDtos.LabourCodeDto;
import com.mes.routing.referencedata.api.dto.ReferenceDataDtos.LabourPlanTypeDto;
import com.mes.routing.referencedata.api.dto.ReferenceDataDtos.RouteTypeDto;
import com.mes.routing.referencedata.api.dto.ReferenceDataDtos.SignificantProcessTypeDto;
import com.mes.routing.referencedata.api.dto.ReferenceDataDtos.SupplierDto;
import com.mes.routing.referencedata.api.dto.ReferenceDataDtos.WorkCentreDto;
import com.mes.routing.referencedata.service.ReferenceDataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Thin delegation test for every reference-data endpoint (controller line coverage). */
@ExtendWith(MockitoExtension.class)
class ReferenceDataControllerTest {

    static final UUID ORG = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID ID = UUID.randomUUID();

    @Mock ReferenceDataService service;
    @InjectMocks ReferenceDataController controller;

    private final Jwt jwt = Jwt.withTokenValue("t").header("alg", "none")
            .claim("org_id", ORG.toString()).build();

    private final WorkCentreDto wc = new WorkCentreDto(ID, "WC", "Cell", null, true);
    private final LabourPlanTypeDto lpt = new LabourPlanTypeDto(ID, "LPT", "Machine", false, true);
    private final LabourCodeDto lc = new LabourCodeDto(ID, "LC", "Welder", null, true);
    private final RouteTypeDto rt = new RouteTypeDto(ID, "NPI", "NPI", false, false, true);
    private final SignificantProcessTypeDto spt =
            new SignificantProcessTypeDto(ID, "BRAZE", "Brazing", "SME", true);
    private final SupplierDto sup = new SupplierDto(ID, "V1", "Vendor", true);

    @Test
    void workCentreEndpointsDelegate() {
        when(service.listWorkCentres(ORG)).thenReturn(List.of(wc));
        when(service.createWorkCentre(ORG, wc)).thenReturn(wc);
        when(service.updateWorkCentre(ORG, ID, wc)).thenReturn(wc);
        assertThat(controller.listWorkCentres(jwt)).containsExactly(wc);
        assertThat(controller.createWorkCentre(jwt, wc).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.updateWorkCentre(jwt, ID, wc)).isEqualTo(wc);
        assertThat(controller.deleteWorkCentre(jwt, ID).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).deleteWorkCentre(ORG, ID);
    }

    @Test
    void labourPlanTypeEndpointsDelegate() {
        when(service.listLabourPlanTypes(ORG)).thenReturn(List.of(lpt));
        when(service.createLabourPlanType(ORG, lpt)).thenReturn(lpt);
        when(service.updateLabourPlanType(ORG, ID, lpt)).thenReturn(lpt);
        assertThat(controller.listLabourPlanTypes(jwt)).containsExactly(lpt);
        assertThat(controller.createLabourPlanType(jwt, lpt).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.updateLabourPlanType(jwt, ID, lpt)).isEqualTo(lpt);
        assertThat(controller.deleteLabourPlanType(jwt, ID).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).deleteLabourPlanType(ORG, ID);
    }

    @Test
    void labourCodeEndpointsDelegate() {
        when(service.listLabourCodes(ORG)).thenReturn(List.of(lc));
        when(service.createLabourCode(ORG, lc)).thenReturn(lc);
        when(service.updateLabourCode(ORG, ID, lc)).thenReturn(lc);
        assertThat(controller.listLabourCodes(jwt)).containsExactly(lc);
        assertThat(controller.createLabourCode(jwt, lc).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.updateLabourCode(jwt, ID, lc)).isEqualTo(lc);
        assertThat(controller.deleteLabourCode(jwt, ID).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).deleteLabourCode(ORG, ID);
    }

    @Test
    void routeTypeEndpointsDelegate() {
        when(service.listRouteTypes(ORG)).thenReturn(List.of(rt));
        when(service.createRouteType(ORG, rt)).thenReturn(rt);
        when(service.updateRouteType(ORG, ID, rt)).thenReturn(rt);
        assertThat(controller.listRouteTypes(jwt)).containsExactly(rt);
        assertThat(controller.createRouteType(jwt, rt).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.updateRouteType(jwt, ID, rt)).isEqualTo(rt);
        assertThat(controller.deleteRouteType(jwt, ID).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).deleteRouteType(ORG, ID);
    }

    @Test
    void significantProcessTypeEndpointsDelegate() {
        when(service.listSignificantProcessTypes(ORG)).thenReturn(List.of(spt));
        when(service.createSignificantProcessType(ORG, spt)).thenReturn(spt);
        when(service.updateSignificantProcessType(ORG, ID, spt)).thenReturn(spt);
        assertThat(controller.listSignificantProcessTypes(jwt)).containsExactly(spt);
        assertThat(controller.createSignificantProcessType(jwt, spt).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(controller.updateSignificantProcessType(jwt, ID, spt)).isEqualTo(spt);
        assertThat(controller.deleteSignificantProcessType(jwt, ID).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).deleteSignificantProcessType(ORG, ID);
    }

    @Test
    void supplierEndpointsDelegate() {
        when(service.listSuppliers(ORG)).thenReturn(List.of(sup));
        when(service.createSupplier(ORG, sup)).thenReturn(sup);
        when(service.updateSupplier(ORG, ID, sup)).thenReturn(sup);
        assertThat(controller.listSuppliers(jwt)).containsExactly(sup);
        assertThat(controller.createSupplier(jwt, sup).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.updateSupplier(jwt, ID, sup)).isEqualTo(sup);
        assertThat(controller.deleteSupplier(jwt, ID).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).deleteSupplier(ORG, ID);
    }
}
