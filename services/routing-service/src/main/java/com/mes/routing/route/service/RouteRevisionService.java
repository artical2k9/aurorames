package com.mes.routing.route.service;

import com.mes.routing.referencedata.repository.SignificantProcessTypeRepository;
import com.mes.routing.referencedata.repository.SupplierRepository;
import com.mes.routing.route.api.dto.RevisionDtos.PatchOperationContentRequest;
import com.mes.routing.route.api.dto.RouteDtos.OperationDto;
import com.mes.routing.route.api.dto.RouteDtos.RouteDto;
import com.mes.routing.route.domain.OperationStatus;
import com.mes.routing.route.domain.Route;
import com.mes.routing.route.domain.RouteOperation;
import com.mes.routing.route.domain.RouteStatus;
import com.mes.routing.route.repository.RouteOperationRepository;
import com.mes.routing.route.repository.RouteRepository;
import com.mes.routing.service.RoutingConflictException;
import com.mes.routing.service.RoutingNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Two-tier revisioning (US8). A <strong>route revision</strong> reopens an APPROVED route to DRAFT
 * and bumps {@code revision}, allowing structural edits (add/duplicate/resequence/group/delete) that
 * require route-level approval; the prior revision's content is preserved in the Envers history. An
 * <strong>operation revision</strong> reopens a single operation to DRAFT and bumps
 * {@code operationRevision}, pinning {@code governingRouteRevision} to the route revision in force;
 * only the operation's content may change (sequence/grouping stay locked) and it is approved at the
 * operation level (FR-022/R7).
 */
@Service
@Transactional
public class RouteRevisionService {

    private final RouteRepository routes;
    private final RouteOperationRepository operations;
    private final RouteService routeService;
    private final RouteOperationService operationService;
    private final SignificantProcessTypeRepository significantProcessTypes;
    private final SupplierRepository suppliers;
    private final RouteLockGuard lockGuard;

    public RouteRevisionService(RouteRepository routes, RouteOperationRepository operations,
                                RouteService routeService, RouteOperationService operationService,
                                SignificantProcessTypeRepository significantProcessTypes,
                                SupplierRepository suppliers, RouteLockGuard lockGuard) {
        this.routes = routes;
        this.operations = operations;
        this.routeService = routeService;
        this.operationService = operationService;
        this.significantProcessTypes = significantProcessTypes;
        this.suppliers = suppliers;
        this.lockGuard = lockGuard;
    }

    /** Start a route revision: APPROVED → DRAFT, revision + 1. Structural edits then require approval. */
    public RouteDto startRouteRevision(UUID orgId, UUID routeId, String reasonForRevision) {
        Route route = requireRoute(orgId, routeId);
        if (route.getStatus() != RouteStatus.APPROVED) {
            throw new RoutingConflictException("Only an APPROVED route can start a new revision (status "
                    + route.getStatus() + ")");
        }
        route.setStatus(RouteStatus.DRAFT);
        route.setRevision(route.getRevision() + 1);
        if (reasonForRevision != null && !reasonForRevision.isBlank()) {
            route.setReasonForRevision(reasonForRevision);
        }
        lockGuard.acquireFor(route);   // reviser holds the edit lock for the new revision
        routes.save(route);
        return routeService.get(orgId, routeId);
    }

    /** Start an operation revision on an APPROVED route: operation APPROVED → DRAFT, operationRevision + 1. */
    public OperationDto startOperationRevision(UUID orgId, UUID routeId, UUID opId) {
        Route route = requireRoute(orgId, routeId);
        if (route.getStatus() != RouteStatus.APPROVED) {
            throw new RoutingConflictException("Operation revisions apply to an APPROVED route (status "
                    + route.getStatus() + "); use a route revision for structural edits");
        }
        RouteOperation op = requireOperation(routeId, opId);
        op.setOperationStatus(OperationStatus.DRAFT);
        op.setOperationRevision(op.getOperationRevision() + 1);
        op.setGoverningRouteRevision(route.getRevision());
        operations.save(op);
        lockGuard.acquireFor(route);   // reviser holds the lock while editing the operation revision
        routes.save(route);
        return operationService.get(orgId, routeId, opId);
    }

    /** Content-only edits during an operation revision; sequence and grouping are locked. */
    public OperationDto patchOperationContent(UUID orgId, UUID routeId, UUID opId,
                                              PatchOperationContentRequest req) {
        lockGuard.requireHeld(requireRoute(orgId, routeId));
        RouteOperation op = requireOperation(routeId, opId);
        if (op.getOperationStatus() != OperationStatus.DRAFT) {
            throw new RoutingConflictException(
                    "Operation content is editable only during an open operation revision (status "
                            + op.getOperationStatus() + "); start an operation revision first");
        }
        validateReferences(orgId, req.significantProcessTypeId(), req.supplierId());
        if (req.description() != null) {
            op.setDescription(req.description());
        }
        if (req.optional() != null) {
            op.setOptional(req.optional());
        }
        if (req.osp() != null) {
            op.setOsp(req.osp());
        }
        if (req.significantProcessTypeId() != null) {
            op.setSignificantProcessTypeId(req.significantProcessTypeId());
        }
        if (req.supplierId() != null) {
            op.setSupplierId(req.supplierId());
        }
        if (req.labourType() != null) {
            op.setLabourType(RouteOperationService.parseLabourType(req.labourType()));
        }
        if (req.clocking() != null) {
            op.setClocking(req.clocking());
        }
        operations.save(op);
        return operationService.get(orgId, routeId, opId);
    }

    /** Submit an open operation revision for operation-level approval: DRAFT → PENDING_APPROVAL. */
    public OperationDto submitOperation(UUID orgId, UUID routeId, UUID opId) {
        requireRoute(orgId, routeId);
        RouteOperation op = requireOperation(routeId, opId);
        if (op.getOperationStatus() != OperationStatus.DRAFT) {
            throw new RoutingConflictException("Only an operation revision in DRAFT can be submitted (status "
                    + op.getOperationStatus() + ")");
        }
        op.setOperationStatus(OperationStatus.PENDING_APPROVAL);
        operations.save(op);
        return operationService.get(orgId, routeId, opId);
    }

    private void validateReferences(UUID orgId, UUID significantProcessTypeId, UUID supplierId) {
        if (significantProcessTypeId != null
                && significantProcessTypes.findByOrgIdAndId(orgId, significantProcessTypeId).isEmpty()) {
            throw new RoutingNotFoundException("Significant process type not found: " + significantProcessTypeId);
        }
        if (supplierId != null && suppliers.findByOrgIdAndId(orgId, supplierId).isEmpty()) {
            throw new RoutingNotFoundException("Supplier not found: " + supplierId);
        }
    }

    private Route requireRoute(UUID orgId, UUID routeId) {
        return routes.findByOrgIdAndId(orgId, routeId)
                .orElseThrow(() -> new RoutingNotFoundException("Route not found: " + routeId));
    }

    private RouteOperation requireOperation(UUID routeId, UUID opId) {
        return operations.findByRouteIdAndId(routeId, opId)
                .orElseThrow(() -> new RoutingNotFoundException("Operation not found: " + opId));
    }
}
