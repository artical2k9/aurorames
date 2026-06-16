package com.mes.routing.approval.service;

import com.mes.routing.approval.api.dto.ApprovalDtos.ApprovalHistoryDto;
import com.mes.routing.approval.api.dto.ApprovalDtos.ApprovalRecordDto;
import com.mes.routing.approval.api.dto.ApprovalDtos.ApproveRequest;
import com.mes.routing.approval.api.dto.ApprovalDtos.RejectRequest;
import com.mes.routing.approval.api.dto.ApprovalDtos.RouteApprovalStatusDto;
import com.mes.routing.approval.api.dto.ApprovalDtos.RouteRevisionHistoryDto;
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
import com.mes.routing.service.RoutingNotFoundException;
import com.mes.routing.service.RoutingValidationException;
import com.mes.udf.api.JwtClaimsExtractor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Route and operation approval via e-signature (US7) and the audit history (US8). A route revision
 * reaches APPROVED once it has one standard approval plus, for each distinct significant-process
 * type on its operations, one approval from a holder of that type's required approver role (FR-024).
 * Every approval re-authenticates the approver's password against Keycloak; failures are not
 * persisted. Approval records are immutable (§V).
 */
@Service
@Transactional
public class ApprovalService {

    private final RouteRepository routes;
    private final RouteOperationRepository operations;
    private final SignificantProcessTypeRepository significantProcessTypes;
    private final ApprovalRecordRepository approvals;
    private final KeycloakCredentialVerifier verifier;
    private final RouteApprovedPublisher publisher;

    public ApprovalService(RouteRepository routes, RouteOperationRepository operations,
                           SignificantProcessTypeRepository significantProcessTypes,
                           ApprovalRecordRepository approvals, KeycloakCredentialVerifier verifier,
                           RouteApprovedPublisher publisher) {
        this.routes = routes;
        this.operations = operations;
        this.significantProcessTypes = significantProcessTypes;
        this.approvals = approvals;
        this.verifier = verifier;
        this.publisher = publisher;
    }

    // ── Route approval lifecycle (US7) ──────────────────────────────────────────────

    public RouteApprovalStatusDto submit(UUID orgId, UUID routeId) {
        Route route = requireRoute(orgId, routeId);
        if (route.getStatus() != RouteStatus.DRAFT) {
            throw new RoutingConflictException("Only a DRAFT route can be submitted (status "
                    + route.getStatus() + ")");
        }
        route.setStatus(RouteStatus.PENDING_APPROVAL);
        routes.save(route);
        return statusOf(routeId, route);
    }

    public RouteApprovalStatusDto approve(UUID orgId, UUID routeId, Jwt jwt, ApproveRequest req) {
        Route route = requireRoute(orgId, routeId);
        if (route.getStatus() != RouteStatus.PENDING_APPROVAL) {
            throw new RoutingConflictException("Route is not awaiting approval (status "
                    + route.getStatus() + ")");
        }
        verifyEsign(jwt, req.password());

        ApprovalRecord record = baseRecord(orgId, routeId, ApprovalSubjectType.ROUTE_REVISION,
                routeId, route.getRevision(), jwt, req.meaning());
        if (req.significantProcessTypeId() != null) {
            record.setSignificantProcessApprover(true);
            record.setSignificantProcessTypeId(req.significantProcessTypeId());
            record.setActorRole(validateSignificantProcessApprover(orgId, routeId,
                    req.significantProcessTypeId(), jwt));
        }
        approvals.save(record);

        if (isRouteRevisionComplete(routeId, route.getRevision())) {
            markRouteApproved(routeId, route, jwt);
        }
        return statusOf(routeId, route);
    }

    public RouteApprovalStatusDto reject(UUID orgId, UUID routeId, Jwt jwt, RejectRequest req) {
        Route route = requireRoute(orgId, routeId);
        if (route.getStatus() != RouteStatus.PENDING_APPROVAL) {
            throw new RoutingConflictException("Route is not awaiting approval (status "
                    + route.getStatus() + ")");
        }
        verifyEsign(jwt, req.password());
        route.setStatus(RouteStatus.REJECTED);
        routes.save(route);
        return statusOf(routeId, route);
    }

    @Transactional(readOnly = true)
    public RouteApprovalStatusDto status(UUID orgId, UUID routeId) {
        return statusOf(routeId, requireRoute(orgId, routeId));
    }

    // ── Operation-level approval (US8) ──────────────────────────────────────────────

    public ApprovalRecordDto approveOperation(UUID orgId, UUID routeId, UUID opId, Jwt jwt,
                                              ApproveRequest req) {
        Route route = requireRoute(orgId, routeId);
        RouteOperation op = operations.findByRouteIdAndId(routeId, opId)
                .orElseThrow(() -> new RoutingNotFoundException("Operation not found: " + opId));
        if (op.getOperationStatus() != OperationStatus.PENDING_APPROVAL) {
            throw new RoutingConflictException("Operation is not awaiting approval (status "
                    + op.getOperationStatus() + "); start an operation revision and submit it first");
        }
        verifyEsign(jwt, req.password());

        ApprovalRecord record = baseRecord(orgId, routeId, ApprovalSubjectType.OPERATION_REVISION,
                opId, op.getOperationRevision(), jwt, req.meaning());
        record.setGoverningRouteRevision(op.getGoverningRouteRevision() != null
                ? op.getGoverningRouteRevision() : route.getRevision());
        ApprovalRecord saved = approvals.save(record);

        op.setOperationStatus(OperationStatus.APPROVED);
        operations.save(op);
        return toDto(saved);
    }

    // ── Audit history (US8) ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApprovalHistoryDto history(UUID orgId, UUID routeId) {
        requireRoute(orgId, routeId);
        List<ApprovalRecord> all = approvals.findByRouteIdOrderByCreatedAt(routeId);
        Set<Integer> revisions = new TreeSet<>();
        all.forEach(r -> revisions.add(governingRevisionOf(r)));
        List<RouteRevisionHistoryDto> grouped = revisions.stream().map(rev -> {
            List<ApprovalRecordDto> routeApprovals = all.stream()
                    .filter(r -> r.getSubjectType() == ApprovalSubjectType.ROUTE_REVISION && r.getRevision() == rev)
                    .map(this::toDto).toList();
            List<ApprovalRecordDto> opApprovals = all.stream()
                    .filter(r -> r.getSubjectType() == ApprovalSubjectType.OPERATION_REVISION
                            && governingRevisionOf(r) == rev)
                    .map(this::toDto).toList();
            return new RouteRevisionHistoryDto(rev, routeApprovals, opApprovals);
        }).toList();
        return new ApprovalHistoryDto(routeId, grouped);
    }

    private int governingRevisionOf(ApprovalRecord r) {
        if (r.getSubjectType() == ApprovalSubjectType.ROUTE_REVISION) {
            return r.getRevision();
        }
        return r.getGoverningRouteRevision() != null ? r.getGoverningRouteRevision() : r.getRevision();
    }

    // ── Internals ───────────────────────────────────────────────────────────────────

    private void verifyEsign(Jwt jwt, String password) {
        String username = jwt.getClaimAsString("preferred_username");
        if (!verifier.verify(username, password)) {
            throw new RoutingValidationException("Electronic signature verification failed");
        }
    }

    private ApprovalRecord baseRecord(UUID orgId, UUID routeId, ApprovalSubjectType type, UUID subjectId,
                                      int revision, Jwt jwt, String meaning) {
        ApprovalRecord record = new ApprovalRecord();
        record.setOrgId(orgId);
        record.setRouteId(routeId);
        record.setSubjectType(type);
        record.setSubjectId(subjectId);
        record.setRevision(revision);
        record.setActor(JwtClaimsExtractor.nullSafeSubject(jwt));
        record.setMeaning(meaning);
        record.setSignedAt(Instant.now());
        return record;
    }

    /** Validates the type is significant on this route and the signer holds its approver role. */
    private String validateSignificantProcessApprover(UUID orgId, UUID routeId, UUID typeId, Jwt jwt) {
        if (!distinctSignificantProcessTypeIds(routeId).contains(typeId)) {
            throw new RoutingValidationException(
                    "Significant-process type is not used on this route: " + typeId);
        }
        SignificantProcessType type = significantProcessTypes.findByOrgIdAndId(orgId, typeId)
                .orElseThrow(() -> new RoutingNotFoundException("Significant process type not found: " + typeId));
        if (!rolesOf(jwt).contains(type.getRequiredApproverRole())) {
            throw new RoutingValidationException("Signer does not hold the required approver role '"
                    + type.getRequiredApproverRole() + "' for significant process " + type.getCode());
        }
        return type.getRequiredApproverRole();
    }

    private boolean isRouteRevisionComplete(UUID routeId, int revision) {
        List<ApprovalRecord> records = approvals.findBySubjectTypeAndSubjectIdAndRevision(
                ApprovalSubjectType.ROUTE_REVISION, routeId, revision);
        boolean hasStandard = records.stream().anyMatch(r -> !r.isSignificantProcessApprover());
        Set<UUID> satisfied = new LinkedHashSet<>();
        records.stream().filter(ApprovalRecord::isSignificantProcessApprover)
                .map(ApprovalRecord::getSignificantProcessTypeId).filter(Objects::nonNull)
                .forEach(satisfied::add);
        return hasStandard && satisfied.containsAll(distinctSignificantProcessTypeIds(routeId));
    }

    private void markRouteApproved(UUID routeId, Route route, Jwt jwt) {
        route.setStatus(RouteStatus.APPROVED);
        routes.save(route);
        operations.findByRouteIdOrderBySequenceNumberAscOperationNumberAsc(routeId).forEach(op -> {
            op.setOperationStatus(OperationStatus.APPROVED);
            op.setGoverningRouteRevision(route.getRevision());
            operations.save(op);
        });
        publisher.publish(new RouteApprovedEvent(routeId, route.getPartId(), route.getPartRevision(),
                route.getRevision(), JwtClaimsExtractor.nullSafeSubject(jwt), Instant.now()));
    }

    private Set<UUID> distinctSignificantProcessTypeIds(UUID routeId) {
        Set<UUID> ids = new LinkedHashSet<>();
        operations.findByRouteIdOrderBySequenceNumberAscOperationNumberAsc(routeId).stream()
                .map(RouteOperation::getSignificantProcessTypeId).filter(Objects::nonNull).forEach(ids::add);
        return ids;
    }

    private RouteApprovalStatusDto statusOf(UUID routeId, Route route) {
        Set<UUID> required = distinctSignificantProcessTypeIds(routeId);
        List<ApprovalRecord> records = approvals.findBySubjectTypeAndSubjectIdAndRevision(
                ApprovalSubjectType.ROUTE_REVISION, routeId, route.getRevision());
        boolean hasStandard = records.stream().anyMatch(r -> !r.isSignificantProcessApprover());
        Set<UUID> satisfied = new LinkedHashSet<>();
        records.stream().filter(ApprovalRecord::isSignificantProcessApprover)
                .map(ApprovalRecord::getSignificantProcessTypeId).filter(Objects::nonNull).forEach(satisfied::add);
        List<UUID> outstanding = required.stream().filter(id -> !satisfied.contains(id))
                .sorted(Comparator.comparing(UUID::toString)).toList();
        return new RouteApprovalStatusDto(routeId, route.getRevision(), route.getStatus().name(),
                hasStandard, outstanding);
    }

    private List<String> rolesOf(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null ? roles : List.of();
    }

    private Route requireRoute(UUID orgId, UUID routeId) {
        return routes.findByOrgIdAndId(orgId, routeId)
                .orElseThrow(() -> new RoutingNotFoundException("Route not found: " + routeId));
    }

    private ApprovalRecordDto toDto(ApprovalRecord r) {
        return new ApprovalRecordDto(r.getId(), r.getSubjectType(), r.getSubjectId(), r.getRevision(),
                r.getActor(), r.getActorRole(), r.getMeaning(), r.getSignedAt(),
                r.isSignificantProcessApprover(), r.getSignificantProcessTypeId(),
                r.getGoverningRouteRevision());
    }
}
