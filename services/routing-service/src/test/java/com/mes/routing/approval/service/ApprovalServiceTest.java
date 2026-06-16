package com.mes.routing.approval.service;

import com.mes.routing.approval.api.dto.ApprovalDtos.ApproveRequest;
import com.mes.routing.approval.api.dto.ApprovalDtos.RejectRequest;
import com.mes.routing.approval.api.dto.ApprovalDtos.RouteApprovalStatusDto;
import com.mes.routing.approval.client.KeycloakCredentialVerifier;
import com.mes.routing.approval.domain.ApprovalRecord;
import com.mes.routing.approval.domain.ApprovalSubjectType;
import com.mes.routing.approval.repository.ApprovalRecordRepository;
import com.mes.routing.kafka.RouteApprovedEvent;
import com.mes.routing.kafka.RouteApprovedPublisher;
import com.mes.routing.referencedata.domain.SignificantProcessType;
import com.mes.routing.referencedata.repository.SignificantProcessTypeRepository;
import com.mes.routing.route.domain.OperationStatus;
import com.mes.routing.route.domain.Route;
import com.mes.routing.route.domain.RouteOperation;
import com.mes.routing.route.domain.RouteStatus;
import com.mes.routing.route.repository.RouteOperationRepository;
import com.mes.routing.route.repository.RouteRepository;
import com.mes.routing.service.RoutingConflictException;
import com.mes.routing.service.RoutingValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
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
class ApprovalServiceTest {

    static final UUID ORG = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID ROUTE = UUID.randomUUID();

    @Mock RouteRepository routes;
    @Mock RouteOperationRepository operations;
    @Mock SignificantProcessTypeRepository significantProcessTypes;
    @Mock ApprovalRecordRepository approvals;
    @Mock KeycloakCredentialVerifier verifier;
    @Mock RouteApprovedPublisher publisher;
    @Mock com.mes.routing.route.service.RouteLockGuard lockGuard;

    @InjectMocks ApprovalService service;

    private Jwt jwt(String... roles) {
        return Jwt.withTokenValue("t").header("alg", "none")
                .claim("sub", "user-1").claim("preferred_username", "approver")
                .claim("roles", List.of(roles)).build();
    }

    private Route route(RouteStatus status, int revision) {
        Route r = new Route();
        r.setOrgId(ORG);
        r.setStatus(status);
        r.setRevision(revision);
        r.setPartId(UUID.randomUUID());
        r.setPartRevision("A");
        return r;
    }

    private RouteOperation opWithSigType(UUID typeId) {
        RouteOperation op = new RouteOperation();
        op.setSignificantProcessTypeId(typeId);
        return op;
    }

    private ApprovalRecord standardRecord() {
        ApprovalRecord r = new ApprovalRecord();
        r.setSubjectType(ApprovalSubjectType.ROUTE_REVISION);
        r.setSignificantProcessApprover(false);
        return r;
    }

    private ApprovalRecord smeRecord(UUID typeId) {
        ApprovalRecord r = new ApprovalRecord();
        r.setSubjectType(ApprovalSubjectType.ROUTE_REVISION);
        r.setSignificantProcessApprover(true);
        r.setSignificantProcessTypeId(typeId);
        return r;
    }

    // ── Submit ─────────────────────────────────────────────────────────────────────

    @Test
    void submit_draftRoute_setsPending() {
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.DRAFT, 1)));
        when(routes.save(any(Route.class))).thenAnswer(inv -> inv.getArgument(0));
        when(operations.findByRouteIdOrderBySequenceNumberAscOperationNumberAsc(ROUTE)).thenReturn(List.of());
        when(approvals.findBySubjectTypeAndSubjectIdAndRevision(any(), eq(ROUTE), eq(1)))
                .thenReturn(List.of());

        RouteApprovalStatusDto dto = service.submit(ORG, ROUTE);

        assertThat(dto.status()).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void submit_notDraft_throwsConflict() {
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.APPROVED, 1)));
        assertThatThrownBy(() -> service.submit(ORG, ROUTE)).isInstanceOf(RoutingConflictException.class);
    }

    // ── Approve ────────────────────────────────────────────────────────────────────

    @Test
    void approve_notPending_throwsConflict() {
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.DRAFT, 1)));
        assertThatThrownBy(() -> service.approve(ORG, ROUTE, jwt(),
                new ApproveRequest("goodpass", "Approve", null)))
                .isInstanceOf(RoutingConflictException.class);
    }

    @Test
    void approve_badPassword_throwsValidationAndDoesNotPersist() {
        when(routes.findByOrgIdAndId(ORG, ROUTE))
                .thenReturn(Optional.of(route(RouteStatus.PENDING_APPROVAL, 1)));
        when(verifier.verify(any(), eq("wrong"))).thenReturn(false);

        assertThatThrownBy(() -> service.approve(ORG, ROUTE, jwt(),
                new ApproveRequest("wrong", "Approve", null)))
                .isInstanceOf(RoutingValidationException.class);
        verify(approvals, never()).save(any());
    }

    @Test
    void approve_standardNoSignificantProcess_approvesAndEmits() {
        Route route = route(RouteStatus.PENDING_APPROVAL, 1);
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route));
        when(routes.save(any(Route.class))).thenAnswer(inv -> inv.getArgument(0));
        when(verifier.verify(any(), eq("goodpass"))).thenReturn(true);
        when(operations.findByRouteIdOrderBySequenceNumberAscOperationNumberAsc(ROUTE)).thenReturn(List.of());
        when(approvals.save(any(ApprovalRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        when(approvals.findBySubjectTypeAndSubjectIdAndRevision(
                ApprovalSubjectType.ROUTE_REVISION, ROUTE, 1)).thenReturn(List.of(standardRecord()));

        RouteApprovalStatusDto dto = service.approve(ORG, ROUTE, jwt(),
                new ApproveRequest("goodpass", "Approve", null));

        assertThat(dto.status()).isEqualTo("APPROVED");
        verify(publisher).publish(any(RouteApprovedEvent.class));
    }

    @Test
    void approve_withSignificantProcess_standardOnly_staysPending() {
        UUID typeId = UUID.randomUUID();
        Route route = route(RouteStatus.PENDING_APPROVAL, 1);
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route));
        when(verifier.verify(any(), eq("goodpass"))).thenReturn(true);
        when(operations.findByRouteIdOrderBySequenceNumberAscOperationNumberAsc(ROUTE))
                .thenReturn(List.of(opWithSigType(typeId)));
        when(approvals.save(any(ApprovalRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        when(approvals.findBySubjectTypeAndSubjectIdAndRevision(
                ApprovalSubjectType.ROUTE_REVISION, ROUTE, 1)).thenReturn(List.of(standardRecord()));

        RouteApprovalStatusDto dto = service.approve(ORG, ROUTE, jwt(),
                new ApproveRequest("goodpass", "Approve", null));

        assertThat(dto.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(dto.outstandingSignificantProcessTypeIds()).containsExactly(typeId);
        verify(publisher, never()).publish(any());
    }

    @Test
    void approve_sme_wrongRole_throwsValidation() {
        UUID typeId = UUID.randomUUID();
        when(routes.findByOrgIdAndId(ORG, ROUTE))
                .thenReturn(Optional.of(route(RouteStatus.PENDING_APPROVAL, 1)));
        when(verifier.verify(any(), eq("goodpass"))).thenReturn(true);
        when(operations.findByRouteIdOrderBySequenceNumberAscOperationNumberAsc(ROUTE))
                .thenReturn(List.of(opWithSigType(typeId)));
        SignificantProcessType type = new SignificantProcessType();
        type.setCode("WELD");
        type.setRequiredApproverRole("WELD_SME");
        when(significantProcessTypes.findByOrgIdAndId(ORG, typeId)).thenReturn(Optional.of(type));

        assertThatThrownBy(() -> service.approve(ORG, ROUTE, jwt("ENGINEER"),
                new ApproveRequest("goodpass", "SME approve", typeId)))
                .isInstanceOf(RoutingValidationException.class);
    }

    @Test
    void approve_smeCompletesWithStandard_approves() {
        UUID typeId = UUID.randomUUID();
        Route route = route(RouteStatus.PENDING_APPROVAL, 1);
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route));
        when(routes.save(any(Route.class))).thenAnswer(inv -> inv.getArgument(0));
        when(verifier.verify(any(), eq("goodpass"))).thenReturn(true);
        when(operations.findByRouteIdOrderBySequenceNumberAscOperationNumberAsc(ROUTE))
                .thenReturn(List.of(opWithSigType(typeId)));
        SignificantProcessType type = new SignificantProcessType();
        type.setCode("WELD");
        type.setRequiredApproverRole("WELD_SME");
        when(significantProcessTypes.findByOrgIdAndId(ORG, typeId)).thenReturn(Optional.of(type));
        when(approvals.save(any(ApprovalRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        // Standard + SME already recorded → complete.
        when(approvals.findBySubjectTypeAndSubjectIdAndRevision(
                ApprovalSubjectType.ROUTE_REVISION, ROUTE, 1))
                .thenReturn(List.of(standardRecord(), smeRecord(typeId)));

        RouteApprovalStatusDto dto = service.approve(ORG, ROUTE, jwt("WELD_SME"),
                new ApproveRequest("goodpass", "SME approve", typeId));

        assertThat(dto.status()).isEqualTo("APPROVED");
        verify(publisher).publish(any(RouteApprovedEvent.class));
    }

    // ── Reject ─────────────────────────────────────────────────────────────────────

    @Test
    void reject_pending_setsRejected() {
        when(routes.findByOrgIdAndId(ORG, ROUTE))
                .thenReturn(Optional.of(route(RouteStatus.PENDING_APPROVAL, 1)));
        when(routes.save(any(Route.class))).thenAnswer(inv -> inv.getArgument(0));
        when(verifier.verify(any(), eq("goodpass"))).thenReturn(true);
        when(operations.findByRouteIdOrderBySequenceNumberAscOperationNumberAsc(ROUTE)).thenReturn(List.of());
        when(approvals.findBySubjectTypeAndSubjectIdAndRevision(any(), eq(ROUTE), eq(1)))
                .thenReturn(List.of());

        RouteApprovalStatusDto dto = service.reject(ORG, ROUTE, jwt(),
                new RejectRequest("goodpass", "Bad sequence"));

        assertThat(dto.status()).isEqualTo("REJECTED");
    }

    // ── Operation approval ───────────────────────────────────────────────────────────

    @Test
    void approveOperation_notPending_throwsConflict() {
        UUID opId = UUID.randomUUID();
        RouteOperation op = new RouteOperation();
        op.setOperationStatus(OperationStatus.DRAFT);
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.APPROVED, 1)));
        when(operations.findByRouteIdAndId(ROUTE, opId)).thenReturn(Optional.of(op));

        assertThatThrownBy(() -> service.approveOperation(ORG, ROUTE, opId, jwt(),
                new ApproveRequest("goodpass", "Approve op", null)))
                .isInstanceOf(RoutingConflictException.class);
    }

    @Test
    void approveOperation_pending_approvesAndRecords() {
        UUID opId = UUID.randomUUID();
        RouteOperation op = new RouteOperation();
        op.setOperationStatus(OperationStatus.PENDING_APPROVAL);
        op.setOperationRevision(2);
        op.setGoverningRouteRevision(1);
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route(RouteStatus.APPROVED, 1)));
        when(operations.findByRouteIdAndId(ROUTE, opId)).thenReturn(Optional.of(op));
        when(verifier.verify(any(), eq("goodpass"))).thenReturn(true);
        when(approvals.save(any(ApprovalRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        var dto = service.approveOperation(ORG, ROUTE, opId, jwt(),
                new ApproveRequest("goodpass", "Approve op", null));

        assertThat(dto.subjectType()).isEqualTo(ApprovalSubjectType.OPERATION_REVISION);
        assertThat(op.getOperationStatus()).isEqualTo(OperationStatus.APPROVED);
    }
}
