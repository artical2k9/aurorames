package com.mes.routing.route.service;

import com.mes.routing.referencedata.repository.SignificantProcessTypeRepository;
import com.mes.routing.referencedata.repository.SupplierRepository;
import com.mes.routing.route.api.dto.RouteDtos.CreateOperationRequest;
import com.mes.routing.route.api.dto.RouteDtos.OperationDto;
import com.mes.routing.route.domain.Route;
import com.mes.routing.route.domain.RouteOperation;
import com.mes.routing.route.domain.RouteStatus;
import com.mes.routing.route.repository.RouteOperationRepository;
import com.mes.routing.route.repository.RouteRepository;
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
class RouteOperationServiceTest {

    static final UUID ORG = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID ROUTE = UUID.randomUUID();

    @Mock RouteRepository routes;
    @Mock RouteOperationRepository operations;
    @Mock SignificantProcessTypeRepository significantProcessTypes;
    @Mock SupplierRepository suppliers;

    @InjectMocks RouteOperationService service;

    private Route route(RouteStatus status) {
        Route r = new Route();
        r.setOrgId(ORG);
        r.setStatus(status);
        return r;
    }

    private CreateOperationRequest op(int number, int seq) {
        return new CreateOperationRequest(number, seq, "Op", false, false, null, null, null);
    }

    @Test
    void add_duplicateOperationNumber_throwsConflict() {
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.DRAFT)));
        when(operations.existsByRouteIdAndOperationNumber(ROUTE, 10)).thenReturn(true);

        assertThatThrownBy(() -> service.add(ORG, ROUTE, op(10, 10)))
                .isInstanceOf(RoutingConflictException.class);
        verify(operations, never()).save(any());
    }

    @Test
    void add_toApprovedRoute_throwsConflict() {
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.APPROVED)));

        assertThatThrownBy(() -> service.add(ORG, ROUTE, op(10, 10)))
                .isInstanceOf(RoutingConflictException.class);
        verify(operations, never()).save(any());
    }

    @Test
    void add_uniqueSequence_derivesNormal() {
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.DRAFT)));
        when(operations.existsByRouteIdAndOperationNumber(ROUTE, 10)).thenReturn(false);
        when(operations.save(any(RouteOperation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(operations.countByRouteIdAndSequenceNumber(ROUTE, 10)).thenReturn(1);

        OperationDto dto = service.add(ORG, ROUTE, op(10, 10));

        assertThat(dto.derivedType()).isEqualTo("NORMAL");
    }

    @Test
    void add_sharedSequence_derivesParallel() {
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.DRAFT)));
        when(operations.existsByRouteIdAndOperationNumber(ROUTE, 40)).thenReturn(false);
        when(operations.save(any(RouteOperation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(operations.countByRouteIdAndSequenceNumber(ROUTE, 50)).thenReturn(2);

        OperationDto dto = service.add(ORG, ROUTE, op(40, 50));

        assertThat(dto.derivedType()).isEqualTo("PARALLEL");
    }
}
