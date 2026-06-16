package com.mes.routing.route.service;

import com.mes.routing.route.api.dto.FlowDtos.CreateStepRequest;
import com.mes.routing.route.api.dto.FlowDtos.GroupInput;
import com.mes.routing.route.api.dto.FlowDtos.MutuallyExclusiveSetDto;
import com.mes.routing.route.api.dto.FlowDtos.MutuallyExclusiveSetInput;
import com.mes.routing.route.api.dto.FlowDtos.PatchStepRequest;
import com.mes.routing.route.api.dto.FlowDtos.PutGroupsRequest;
import com.mes.routing.route.api.dto.FlowDtos.PutMutuallyExclusiveSetsRequest;
import com.mes.routing.route.api.dto.FlowDtos.StepDto;
import com.mes.routing.route.domain.MutuallyExclusiveLevel;
import com.mes.routing.route.domain.MutuallyExclusiveSet;
import com.mes.routing.route.domain.OperationStep;
import com.mes.routing.route.domain.Route;
import com.mes.routing.route.domain.RouteOperation;
import com.mes.routing.route.domain.RouteStatus;
import com.mes.routing.route.repository.MutuallyExclusiveSetRepository;
import com.mes.routing.route.repository.OperationGroupRepository;
import com.mes.routing.route.repository.OperationStepRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationFlowServiceTest {

    static final UUID ORG = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID ROUTE = UUID.randomUUID();
    static final UUID OP = UUID.randomUUID();

    @Mock RouteRepository routes;
    @Mock RouteOperationRepository operations;
    @Mock OperationGroupRepository groups;
    @Mock OperationStepRepository steps;
    @Mock MutuallyExclusiveSetRepository meSets;

    @Mock RouteLockGuard lockGuard;
    @InjectMocks OperationFlowService service;

    private Route route(RouteStatus status) {
        Route r = new Route();
        r.setOrgId(ORG);
        r.setStatus(status);
        return r;
    }

    private void draftRoute() {
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.DRAFT)));
    }

    private RouteOperation operation(int seq) {
        RouteOperation op = new RouteOperation();
        op.setOrgId(ORG);
        op.setRouteId(ROUTE);
        op.setSequenceNumber(seq);
        return op;
    }

    // ── Groups ───────────────────────────────────────────────────────────────────

    @Test
    void listGroups_unknownRoute_throwsNotFound() {
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.listGroups(ORG, ROUTE))
                .isInstanceOf(RoutingNotFoundException.class);
    }

    @Test
    void putGroups_notDraft_throwsConflict() {
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.APPROVED)));
        assertThatThrownBy(() -> service.putGroups(ORG, ROUTE,
                new PutGroupsRequest(List.of(new GroupInput("G", 10, false, List.of())))))
                .isInstanceOf(RoutingConflictException.class);
    }

    @Test
    void putGroups_operationNotOnRoute_throwsValidation() {
        draftRoute();
        when(operations.findByRouteIdOrderBySequenceNumberAscOperationNumberAsc(ROUTE))
                .thenReturn(List.of());
        assertThatThrownBy(() -> service.putGroups(ORG, ROUTE,
                new PutGroupsRequest(List.of(new GroupInput("G", 10, false, List.of(UUID.randomUUID()))))))
                .isInstanceOf(RoutingValidationException.class);
    }

    // (duplicate-group-assignment guard is covered against real persisted ids in OperationGroupIT)

    // ── Steps ────────────────────────────────────────────────────────────────────

    @Test
    void addStep_valid_derivesNormal() {
        draftRoute();
        when(operations.findByRouteIdAndId(ROUTE, OP)).thenReturn(Optional.of(new RouteOperation()));
        when(steps.existsByOperationIdAndStepNumber(OP, 1)).thenReturn(false);
        when(steps.save(any(OperationStep.class))).thenAnswer(inv -> inv.getArgument(0));
        when(steps.countByOperationIdAndStepSequenceNumber(OP, 10)).thenReturn(1);

        StepDto dto = service.addStep(ORG, ROUTE, OP, new CreateStepRequest(1, 10, "Deburr", false));

        assertThat(dto.stepNumber()).isEqualTo(1);
        assertThat(dto.derivedType()).isEqualTo("NORMAL");
    }

    @Test
    void addStep_duplicateNumber_throwsConflict() {
        draftRoute();
        when(operations.findByRouteIdAndId(ROUTE, OP)).thenReturn(Optional.of(new RouteOperation()));
        when(steps.existsByOperationIdAndStepNumber(OP, 1)).thenReturn(true);
        assertThatThrownBy(() -> service.addStep(ORG, ROUTE, OP, new CreateStepRequest(1, 10, "x", false)))
                .isInstanceOf(RoutingConflictException.class);
    }

    @Test
    void addStep_routeNotDraft_throwsConflict() {
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.APPROVED)));
        assertThatThrownBy(() -> service.addStep(ORG, ROUTE, OP, new CreateStepRequest(1, 10, "x", false)))
                .isInstanceOf(RoutingConflictException.class);
    }

    @Test
    void patchStep_unknown_throwsNotFound() {
        draftRoute();
        when(operations.findByRouteIdAndId(ROUTE, OP)).thenReturn(Optional.of(new RouteOperation()));
        UUID stepId = UUID.randomUUID();
        when(steps.findByOperationIdAndId(OP, stepId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.patchStep(ORG, ROUTE, OP, stepId,
                new PatchStepRequest(null, null, "x", null)))
                .isInstanceOf(RoutingNotFoundException.class);
    }

    @Test
    void patchStep_updatesFields() {
        draftRoute();
        when(operations.findByRouteIdAndId(ROUTE, OP)).thenReturn(Optional.of(new RouteOperation()));
        OperationStep s = new OperationStep();
        s.setOperationId(OP);
        s.setStepNumber(1);
        s.setStepSequenceNumber(10);
        UUID stepId = UUID.randomUUID();
        when(steps.findByOperationIdAndId(OP, stepId)).thenReturn(Optional.of(s));
        when(steps.save(any(OperationStep.class))).thenAnswer(inv -> inv.getArgument(0));
        when(steps.countByOperationIdAndStepSequenceNumber(OP, 20)).thenReturn(2);

        StepDto dto = service.patchStep(ORG, ROUTE, OP, stepId,
                new PatchStepRequest(2, 20, "New", true));

        assertThat(dto.stepNumber()).isEqualTo(2);
        assertThat(dto.optional()).isTrue();
        assertThat(dto.derivedType()).isEqualTo("PARALLEL");
    }

    @Test
    void deleteStep_removes() {
        draftRoute();
        when(operations.findByRouteIdAndId(ROUTE, OP)).thenReturn(Optional.of(new RouteOperation()));
        OperationStep s = new OperationStep();
        UUID stepId = UUID.randomUUID();
        when(steps.findByOperationIdAndId(OP, stepId)).thenReturn(Optional.of(s));
        service.deleteStep(ORG, ROUTE, OP, stepId);
        verify(steps).delete(s);
    }

    @Test
    void listSteps_unknownOperation_throwsNotFound() {
        draftRoute();
        when(operations.findByRouteIdAndId(ROUTE, OP)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.listSteps(ORG, ROUTE, OP))
                .isInstanceOf(RoutingNotFoundException.class);
    }

    // ── Mutually-exclusive sets ────────────────────────────────────────────────────

    @Test
    void putMe_operationLevel_valid() {
        draftRoute();
        UUID m1 = UUID.randomUUID();
        UUID m2 = UUID.randomUUID();
        when(operations.findByRouteIdAndId(ROUTE, m1)).thenReturn(Optional.of(operation(30)));
        when(operations.findByRouteIdAndId(ROUTE, m2)).thenReturn(Optional.of(operation(30)));
        when(meSets.save(any(MutuallyExclusiveSet.class))).thenAnswer(inv -> inv.getArgument(0));
        MutuallyExclusiveSet stored = new MutuallyExclusiveSet();
        stored.setLevel(MutuallyExclusiveLevel.OPERATION);
        stored.setSequenceNumber(30);
        stored.setMemberIds(List.of(m1, m2));
        when(meSets.findByRouteIdOrderByLevelAscSequenceNumberAsc(ROUTE)).thenReturn(List.of(stored));

        List<MutuallyExclusiveSetDto> result = service.putMutuallyExclusiveSets(ORG, ROUTE,
                new PutMutuallyExclusiveSetsRequest(List.of(
                        new MutuallyExclusiveSetInput(MutuallyExclusiveLevel.OPERATION, List.of(m1, m2)))));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sequenceNumber()).isEqualTo(30);
        verify(meSets).save(any(MutuallyExclusiveSet.class));
    }

    @Test
    void putMe_membersDifferentSequence_throwsValidation() {
        draftRoute();
        UUID m1 = UUID.randomUUID();
        UUID m2 = UUID.randomUUID();
        when(operations.findByRouteIdAndId(ROUTE, m1)).thenReturn(Optional.of(operation(20)));
        when(operations.findByRouteIdAndId(ROUTE, m2)).thenReturn(Optional.of(operation(30)));
        assertThatThrownBy(() -> service.putMutuallyExclusiveSets(ORG, ROUTE,
                new PutMutuallyExclusiveSetsRequest(List.of(
                        new MutuallyExclusiveSetInput(MutuallyExclusiveLevel.OPERATION, List.of(m1, m2))))))
                .isInstanceOf(RoutingValidationException.class);
    }

    @Test
    void putMe_duplicateMembers_throwsValidation() {
        draftRoute();
        UUID m1 = UUID.randomUUID();
        assertThatThrownBy(() -> service.putMutuallyExclusiveSets(ORG, ROUTE,
                new PutMutuallyExclusiveSetsRequest(List.of(
                        new MutuallyExclusiveSetInput(MutuallyExclusiveLevel.OPERATION, List.of(m1, m1))))))
                .isInstanceOf(RoutingValidationException.class);
    }

    @Test
    void putMe_unknownMember_throwsValidation() {
        draftRoute();
        UUID m1 = UUID.randomUUID();
        UUID m2 = UUID.randomUUID();
        when(operations.findByRouteIdAndId(ROUTE, m1)).thenReturn(Optional.of(operation(30)));
        when(operations.findByRouteIdAndId(ROUTE, m2)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.putMutuallyExclusiveSets(ORG, ROUTE,
                new PutMutuallyExclusiveSetsRequest(List.of(
                        new MutuallyExclusiveSetInput(MutuallyExclusiveLevel.OPERATION, List.of(m1, m2))))))
                .isInstanceOf(RoutingValidationException.class);
    }

    @Test
    void putMe_stepLevel_differentOperations_throwsValidation() {
        draftRoute();
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        OperationStep st1 = new OperationStep();
        st1.setOperationId(UUID.randomUUID());
        st1.setStepSequenceNumber(10);
        OperationStep st2 = new OperationStep();
        st2.setOperationId(UUID.randomUUID());
        st2.setStepSequenceNumber(10);
        when(steps.findById(s1)).thenReturn(Optional.of(st1));
        when(steps.findById(s2)).thenReturn(Optional.of(st2));
        when(operations.findByRouteIdAndId(ROUTE, st1.getOperationId()))
                .thenReturn(Optional.of(new RouteOperation()));
        when(operations.findByRouteIdAndId(ROUTE, st2.getOperationId()))
                .thenReturn(Optional.of(new RouteOperation()));
        assertThatThrownBy(() -> service.putMutuallyExclusiveSets(ORG, ROUTE,
                new PutMutuallyExclusiveSetsRequest(List.of(
                        new MutuallyExclusiveSetInput(MutuallyExclusiveLevel.STEP, List.of(s1, s2))))))
                .isInstanceOf(RoutingValidationException.class);
    }
}
