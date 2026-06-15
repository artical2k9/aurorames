package com.mes.routing.route.service;

import com.mes.routing.referencedata.domain.RouteType;
import com.mes.routing.referencedata.repository.RouteTypeRepository;
import com.mes.routing.route.api.dto.RouteDtos.CreateRouteRequest;
import com.mes.routing.route.api.dto.RouteDtos.PatchRouteRequest;
import com.mes.routing.route.api.dto.RouteDtos.RouteDto;
import com.mes.routing.route.domain.Route;
import com.mes.routing.route.domain.RouteStatus;
import com.mes.routing.route.repository.RouteRepository;
import com.mes.routing.service.RoutingConflictException;
import com.mes.routing.service.RoutingNotFoundException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteServiceTest {

    static final UUID ORG = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID PART = UUID.randomUUID();
    static final UUID TYPE = UUID.randomUUID();

    @Mock RouteRepository routes;
    @Mock RouteTypeRepository routeTypes;

    @InjectMocks RouteService service;

    private CreateRouteRequest req() {
        return new CreateRouteRequest(PART, "A", TYPE, null, null, null, "Initial", null);
    }

    private RouteType type(boolean standard) {
        RouteType t = new RouteType();
        t.setOrgId(ORG);
        t.setCode(standard ? "STANDARD" : "NPI");
        t.setStandard(standard);
        return t;
    }

    @Test
    void create_secondStandardForPart_throwsConflict() {
        when(routeTypes.findByOrgIdAndId(ORG, TYPE)).thenReturn(Optional.of(type(true)));
        when(routes.existsStandardRouteForPart(ORG, PART, "A")).thenReturn(true);

        assertThatThrownBy(() -> service.create(ORG, req())).isInstanceOf(RoutingConflictException.class);
        verify(routes, never()).save(any());
    }

    @Test
    void create_firstStandard_savesDraftRevision1() {
        when(routeTypes.findByOrgIdAndId(ORG, TYPE)).thenReturn(Optional.of(type(true)));
        when(routes.existsStandardRouteForPart(ORG, PART, "A")).thenReturn(false);
        when(routes.save(any(Route.class))).thenAnswer(inv -> inv.getArgument(0));

        RouteDto dto = service.create(ORG, req());

        assertThat(dto.status()).isEqualTo("DRAFT");
        assertThat(dto.revision()).isEqualTo(1);
    }

    @Test
    void create_alternateType_skipsStandardCheck() {
        when(routeTypes.findByOrgIdAndId(ORG, TYPE)).thenReturn(Optional.of(type(false)));
        when(routes.save(any(Route.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(ORG, req());

        verify(routes, never()).existsStandardRouteForPart(any(), any(), any());
    }

    @Test
    void create_unknownRouteType_throwsNotFound() {
        when(routeTypes.findByOrgIdAndId(ORG, TYPE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(ORG, req())).isInstanceOf(RoutingNotFoundException.class);
    }

    @Test
    void patch_approvedRoute_throwsConflict() {
        Route approved = new Route();
        approved.setOrgId(ORG);
        approved.setStatus(RouteStatus.APPROVED);
        UUID id = UUID.randomUUID();
        when(routes.findByOrgIdAndId(ORG, id)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> service.patch(ORG, id,
                new PatchRouteRequest("x", null, null, null, null)))
                .isInstanceOf(RoutingConflictException.class);
        verify(routes, never()).save(any());
    }

    @Test
    void get_unknown_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(routes.findByOrgIdAndId(eq(ORG), eq(id))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(ORG, id)).isInstanceOf(RoutingNotFoundException.class);
    }
}
