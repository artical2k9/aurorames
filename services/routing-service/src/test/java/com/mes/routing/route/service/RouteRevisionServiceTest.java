package com.mes.routing.route.service;

import com.mes.routing.referencedata.repository.SignificantProcessTypeRepository;
import com.mes.routing.referencedata.repository.SupplierRepository;
import com.mes.routing.route.api.dto.RevisionDtos.PatchOperationContentRequest;
import com.mes.routing.route.domain.OperationStatus;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteRevisionServiceTest {

    static final UUID ORG = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID ROUTE = UUID.randomUUID();
    static final UUID OP = UUID.randomUUID();

    @Mock RouteRepository routes;
    @Mock RouteOperationRepository operations;
    @Mock RouteService routeService;
    @Mock RouteOperationService operationService;
    @Mock SignificantProcessTypeRepository significantProcessTypes;
    @Mock SupplierRepository suppliers;

    @InjectMocks RouteRevisionService service;

    private Route route(RouteStatus status, int revision) {
        Route r = new Route();
        r.setOrgId(ORG);
        r.setStatus(status);
        r.setRevision(revision);
        return r;
    }

    private RouteOperation op(OperationStatus status, int rev) {
        RouteOperation o = new RouteOperation();
        o.setOperationStatus(status);
        o.setOperationRevision(rev);
        return o;
    }

    @Test
    void startRouteRevision_notApproved_throwsConflict() {
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.DRAFT, 1)));
        assertThatThrownBy(() -> service.startRouteRevision(ORG, ROUTE, "fix"))
                .isInstanceOf(RoutingConflictException.class);
    }

    @Test
    void startRouteRevision_approved_reopensDraftAndBumps() {
        Route route = route(RouteStatus.APPROVED, 1);
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route));

        service.startRouteRevision(ORG, ROUTE, "Design change");

        assertThat(route.getStatus()).isEqualTo(RouteStatus.DRAFT);
        assertThat(route.getRevision()).isEqualTo(2);
        assertThat(route.getReasonForRevision()).isEqualTo("Design change");
    }

    @Test
    void startOperationRevision_routeNotApproved_throwsConflict() {
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.DRAFT, 1)));
        assertThatThrownBy(() -> service.startOperationRevision(ORG, ROUTE, OP))
                .isInstanceOf(RoutingConflictException.class);
    }

    @Test
    void startOperationRevision_approvedRoute_reopensOperationAndPins() {
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.APPROVED, 3)));
        RouteOperation op = op(OperationStatus.APPROVED, 1);
        when(operations.findByRouteIdAndId(ROUTE, OP)).thenReturn(Optional.of(op));

        service.startOperationRevision(ORG, ROUTE, OP);

        assertThat(op.getOperationStatus()).isEqualTo(OperationStatus.DRAFT);
        assertThat(op.getOperationRevision()).isEqualTo(2);
        assertThat(op.getGoverningRouteRevision()).isEqualTo(3);
    }

    @Test
    void patchOperationContent_notDraft_throwsConflict() {
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.APPROVED, 1)));
        when(operations.findByRouteIdAndId(ROUTE, OP))
                .thenReturn(Optional.of(op(OperationStatus.APPROVED, 1)));
        assertThatThrownBy(() -> service.patchOperationContent(ORG, ROUTE, OP,
                new PatchOperationContentRequest("x", null, null, null, null, null)))
                .isInstanceOf(RoutingConflictException.class);
    }

    @Test
    void patchOperationContent_draft_updatesContent() {
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.APPROVED, 1)));
        RouteOperation op = op(OperationStatus.DRAFT, 2);
        when(operations.findByRouteIdAndId(ROUTE, OP)).thenReturn(Optional.of(op));

        service.patchOperationContent(ORG, ROUTE, OP,
                new PatchOperationContentRequest("New desc", true, false, null, null, null));

        assertThat(op.getDescription()).isEqualTo("New desc");
        assertThat(op.isOptional()).isTrue();
    }

    @Test
    void submitOperation_draft_setsPending() {
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.APPROVED, 1)));
        RouteOperation op = op(OperationStatus.DRAFT, 2);
        when(operations.findByRouteIdAndId(ROUTE, OP)).thenReturn(Optional.of(op));

        service.submitOperation(ORG, ROUTE, OP);

        assertThat(op.getOperationStatus()).isEqualTo(OperationStatus.PENDING_APPROVAL);
    }

    @Test
    void submitOperation_notDraft_throwsConflict() {
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.APPROVED, 1)));
        when(operations.findByRouteIdAndId(ROUTE, OP))
                .thenReturn(Optional.of(op(OperationStatus.APPROVED, 1)));
        assertThatThrownBy(() -> service.submitOperation(ORG, ROUTE, OP))
                .isInstanceOf(RoutingConflictException.class);
    }
}
