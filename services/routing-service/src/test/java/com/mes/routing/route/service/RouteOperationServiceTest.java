package com.mes.routing.route.service;

import com.mes.routing.referencedata.repository.LabourPlanTypeRepository;
import com.mes.routing.referencedata.repository.SignificantProcessTypeRepository;
import com.mes.routing.referencedata.repository.SupplierRepository;
import com.mes.routing.referencedata.domain.LabourPlanType;
import com.mes.routing.referencedata.domain.SignificantProcessType;
import com.mes.routing.referencedata.domain.Supplier;
import com.mes.routing.route.api.dto.RouteDtos.CreateOperationRequest;
import com.mes.routing.route.api.dto.RouteDtos.OperationDto;
import com.mes.routing.route.api.dto.RouteDtos.PatchOperationRequest;
import com.mes.routing.route.domain.LabourPlanLine;
import com.mes.routing.route.domain.Route;
import com.mes.routing.route.domain.RouteOperation;
import com.mes.routing.route.domain.RouteStatus;
import com.mes.routing.route.repository.LabourPlanLineRepository;
import com.mes.routing.route.repository.RouteOperationRepository;
import com.mes.routing.route.repository.RouteRepository;
import com.mes.routing.service.RoutingConflictException;
import com.mes.routing.service.RoutingNotFoundException;
import com.mes.routing.service.RoutingValidationException;
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
import static org.mockito.Mockito.lenient;
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
    @Mock LabourPlanLineRepository labourPlanLines;
    @Mock LabourPlanTypeRepository labourPlanTypes;

    @Mock RouteLockGuard lockGuard;
    @InjectMocks RouteOperationService service;

    /** Make the operation under test carry an OSP-type labour resource so osp=true validates. */
    private void operationHasOspResource() {
        LabourPlanLine line = new LabourPlanLine();
        UUID planTypeId = UUID.randomUUID();
        line.setLabourPlanTypeId(planTypeId);
        LabourPlanType ospType = new LabourPlanType();
        ospType.setCode("OSP");
        lenient().when(labourPlanLines.findByOperationIdOrderByCreatedAt(any())).thenReturn(List.of(line));
        lenient().when(labourPlanTypes.findAllById(any())).thenReturn(List.of(ospType));
    }

    private Route route(RouteStatus status) {
        Route r = new Route();
        r.setOrgId(ORG);
        r.setStatus(status);
        return r;
    }

    private CreateOperationRequest op(int number, int seq) {
        return new CreateOperationRequest(number, seq, "Op", false, false, null, null, null, null);
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

    @Test
    void add_unknownSignificantProcessType_throwsNotFound() {
        UUID sptId = UUID.randomUUID();
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.DRAFT)));
        when(operations.existsByRouteIdAndOperationNumber(ROUTE, 10)).thenReturn(false);
        when(significantProcessTypes.findByOrgIdAndId(ORG, sptId)).thenReturn(Optional.empty());

        CreateOperationRequest req = new CreateOperationRequest(10, 10, "Op", false, false, sptId, null, null, null);
        assertThatThrownBy(() -> service.add(ORG, ROUTE, req)).isInstanceOf(RoutingNotFoundException.class);
    }

    @Test
    void add_unknownSupplier_throwsNotFound() {
        UUID supId = UUID.randomUUID();
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.DRAFT)));
        when(operations.existsByRouteIdAndOperationNumber(ROUTE, 10)).thenReturn(false);
        when(suppliers.findByOrgIdAndId(ORG, supId)).thenReturn(Optional.empty());

        CreateOperationRequest req = new CreateOperationRequest(10, 10, "Op", false, true, null, supId, null, null);
        assertThatThrownBy(() -> service.add(ORG, ROUTE, req)).isInstanceOf(RoutingNotFoundException.class);
    }

    @Test
    void add_withValidReferences_persists() {
        UUID sptId = UUID.randomUUID();
        UUID supId = UUID.randomUUID();
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.DRAFT)));
        when(operations.existsByRouteIdAndOperationNumber(ROUTE, 10)).thenReturn(false);
        when(significantProcessTypes.findByOrgIdAndId(ORG, sptId))
                .thenReturn(Optional.of(new SignificantProcessType()));
        when(suppliers.findByOrgIdAndId(ORG, supId)).thenReturn(Optional.of(new Supplier()));
        when(operations.save(any(RouteOperation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(operations.countByRouteIdAndSequenceNumber(ROUTE, 10)).thenReturn(1);
        operationHasOspResource();

        OperationDto dto = service.add(ORG, ROUTE,
                new CreateOperationRequest(10, 10, "Op", true, true, sptId, supId, null, false));

        assertThat(dto.optional()).isTrue();
        assertThat(dto.osp()).isTrue();
    }

    @Test
    void add_ospWithoutLabourResource_throwsValidation() {
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.DRAFT)));
        when(operations.existsByRouteIdAndOperationNumber(ROUTE, 10)).thenReturn(false);
        when(operations.save(any(RouteOperation.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.add(ORG, ROUTE,
                new CreateOperationRequest(10, 10, "Op", false, true, null, null, null, null)))
                .isInstanceOf(RoutingValidationException.class);
    }

    @Test
    void patch_toOspWithLabourResource_persists() {
        RouteOperation o = new RouteOperation();
        o.setOrgId(ORG);
        o.setRouteId(ROUTE);
        o.setOperationNumber(10);
        o.setSequenceNumber(10);
        UUID opId = UUID.randomUUID();
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.DRAFT)));
        when(operations.findByRouteIdAndId(ROUTE, opId)).thenReturn(Optional.of(o));
        when(operations.save(any(RouteOperation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(operations.countByRouteIdAndSequenceNumber(ROUTE, 10)).thenReturn(1);
        operationHasOspResource();

        OperationDto dto = service.patch(ORG, ROUTE, opId,
                new PatchOperationRequest(null, null, null, null, true, null, null, null, null));

        assertThat(dto.osp()).isTrue();
    }

    @Test
    void list_returnsOperationsWithDerivedType() {
        RouteOperation o = new RouteOperation();
        o.setOrgId(ORG);
        o.setRouteId(ROUTE);
        o.setOperationNumber(10);
        o.setSequenceNumber(10);
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.DRAFT)));
        when(operations.findByRouteIdOrderBySequenceNumberAscOperationNumberAsc(ROUTE))
                .thenReturn(List.of(o));
        when(operations.countByRouteIdAndSequenceNumber(ROUTE, 10)).thenReturn(1);

        List<OperationDto> result = service.list(ORG, ROUTE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).derivedType()).isEqualTo("NORMAL");
    }

    @Test
    void patch_updatesFields() {
        RouteOperation o = new RouteOperation();
        o.setOrgId(ORG);
        o.setRouteId(ROUTE);
        o.setOperationNumber(10);
        o.setSequenceNumber(10);
        UUID opId = UUID.randomUUID();
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.DRAFT)));
        when(operations.findByRouteIdAndId(ROUTE, opId)).thenReturn(Optional.of(o));
        when(operations.save(any(RouteOperation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(operations.countByRouteIdAndSequenceNumber(ROUTE, 20)).thenReturn(1);

        OperationDto dto = service.patch(ORG, ROUTE, opId,
                new PatchOperationRequest(15, 20, "New", true, false, null, null, null, false));

        assertThat(dto.operationNumber()).isEqualTo(15);
        assertThat(dto.sequenceNumber()).isEqualTo(20);
        assertThat(dto.optional()).isTrue();
    }

    @Test
    void add_withoutLabourType_defaultsDirect() {
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.DRAFT)));
        when(operations.existsByRouteIdAndOperationNumber(ROUTE, 10)).thenReturn(false);
        when(operations.save(any(RouteOperation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(operations.countByRouteIdAndSequenceNumber(ROUTE, 10)).thenReturn(1);

        OperationDto dto = service.add(ORG, ROUTE, op(10, 10));

        assertThat(dto.labourType()).isEqualTo("DIRECT");
    }

    @Test
    void patch_updatesLabourType() {
        RouteOperation o = new RouteOperation();
        o.setOrgId(ORG);
        o.setRouteId(ROUTE);
        o.setOperationNumber(10);
        o.setSequenceNumber(10);
        UUID opId = UUID.randomUUID();
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.DRAFT)));
        when(operations.findByRouteIdAndId(ROUTE, opId)).thenReturn(Optional.of(o));
        when(operations.save(any(RouteOperation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(operations.countByRouteIdAndSequenceNumber(ROUTE, 10)).thenReturn(1);

        OperationDto dto = service.patch(ORG, ROUTE, opId,
                new PatchOperationRequest(null, null, null, null, null, null, null, "INDIRECT", null));

        assertThat(dto.labourType()).isEqualTo("INDIRECT");
    }

    @Test
    void patch_invalidLabourType_throwsValidation() {
        RouteOperation o = new RouteOperation();
        o.setOrgId(ORG);
        o.setRouteId(ROUTE);
        o.setOperationNumber(10);
        o.setSequenceNumber(10);
        UUID opId = UUID.randomUUID();
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.DRAFT)));
        when(operations.findByRouteIdAndId(ROUTE, opId)).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> service.patch(ORG, ROUTE, opId,
                new PatchOperationRequest(null, null, null, null, null, null, null, "CAPITALISED", null)))
                .isInstanceOf(RoutingValidationException.class);
    }

    @Test
    void patch_duplicateNumber_throwsConflict() {
        RouteOperation o = new RouteOperation();
        o.setOperationNumber(10);
        UUID opId = UUID.randomUUID();
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.DRAFT)));
        when(operations.findByRouteIdAndId(ROUTE, opId)).thenReturn(Optional.of(o));
        when(operations.existsByRouteIdAndOperationNumber(ROUTE, 20)).thenReturn(true);

        assertThatThrownBy(() -> service.patch(ORG, ROUTE, opId,
                new PatchOperationRequest(20, null, null, null, null, null, null, null, null)))
                .isInstanceOf(RoutingConflictException.class);
    }

    @Test
    void patch_unknownOperation_throwsNotFound() {
        UUID opId = UUID.randomUUID();
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.DRAFT)));
        when(operations.findByRouteIdAndId(ROUTE, opId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.patch(ORG, ROUTE, opId,
                new PatchOperationRequest(null, null, "x", null, null, null, null, null, null)))
                .isInstanceOf(RoutingNotFoundException.class);
    }

    @Test
    void delete_removesOperation() {
        RouteOperation o = new RouteOperation();
        UUID opId = UUID.randomUUID();
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.DRAFT)));
        when(operations.findByRouteIdAndId(ROUTE, opId)).thenReturn(Optional.of(o));

        service.delete(ORG, ROUTE, opId);

        verify(operations).delete(o);
    }

    @Test
    void list_unknownRoute_throwsNotFound() {
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.list(ORG, ROUTE)).isInstanceOf(RoutingNotFoundException.class);
    }
}
