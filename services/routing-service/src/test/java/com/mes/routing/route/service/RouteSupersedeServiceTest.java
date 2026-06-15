package com.mes.routing.route.service;

import com.mes.routing.route.domain.Route;
import com.mes.routing.route.repository.RouteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteSupersedeServiceTest {

    @Mock RouteRepository routes;
    @InjectMocks RouteSupersedeService service;

    @Test
    void markBomRevisionSuperseded_setsFlagOnce() {
        UUID rev = UUID.randomUUID();
        Route route = new Route();
        when(routes.findByBomRevisionId(rev)).thenReturn(List.of(route));

        service.markBomRevisionSuperseded(rev);

        assertThat(route.isBomRevisionSuperseded()).isTrue();
        verify(routes).save(route);
    }

    @Test
    void markBomRevisionSuperseded_idempotentWhenAlreadySet() {
        UUID rev = UUID.randomUUID();
        Route route = new Route();
        route.setBomRevisionSuperseded(true);
        when(routes.findByBomRevisionId(rev)).thenReturn(List.of(route));

        service.markBomRevisionSuperseded(rev);

        verify(routes, never()).save(route);
    }

    @Test
    void markInspectionPlanRevisionSuperseded_setsFlag() {
        UUID rev = UUID.randomUUID();
        Route route = new Route();
        when(routes.findByInspectionPlanRevisionId(rev)).thenReturn(List.of(route));

        service.markInspectionPlanRevisionSuperseded(rev);

        assertThat(route.isInspectionPlanRevisionSuperseded()).isTrue();
        verify(routes).save(route);
    }
}
