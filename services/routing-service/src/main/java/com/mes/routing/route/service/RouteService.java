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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Route header management (US1). Enforces at most one Standard-type route per
 * (org, part, revision) (FR-004b); pins BOM/inspection revisions; edits only while DRAFT
 * (FR-025 — structural/content changes to an approved route go through a revision).
 */
@Service
@Transactional
public class RouteService {

    private final RouteRepository routes;
    private final RouteTypeRepository routeTypes;
    private final RouteLockGuard lockGuard;

    public RouteService(RouteRepository routes, RouteTypeRepository routeTypes, RouteLockGuard lockGuard) {
        this.routes = routes;
        this.routeTypes = routeTypes;
        this.lockGuard = lockGuard;
    }

    public RouteDto create(UUID orgId, CreateRouteRequest req) {
        RouteType type = routeTypes.findByOrgIdAndId(orgId, req.routeTypeId())
                .orElseThrow(() -> new RoutingNotFoundException("Route type not found: " + req.routeTypeId()));
        if (type.isStandard()
                && routes.existsStandardRouteForPart(orgId, req.partId(), req.partRevision())) {
            throw new RoutingConflictException(
                    "A Standard route already exists for this part/revision");
        }
        Route route = new Route();
        route.setOrgId(orgId);
        route.setPartId(req.partId());
        route.setPartRevision(req.partRevision());
        route.setRouteTypeId(req.routeTypeId());
        route.setBomId(req.bomId());
        route.setBomRevisionId(req.bomRevisionId());
        route.setInspectionPlanRevisionId(req.inspectionPlanRevisionId());
        route.setReasonForRevision(req.reasonForRevision());
        route.setRevision(0);
        route.setStatus(RouteStatus.DRAFT);
        route.setCustomFields(req.customFields());
        lockGuard.acquireFor(route);   // creator holds the edit lock (FR-031)
        return toDto(routes.save(route));
    }

    // ── Edit lock (FR-031/032/033) ───────────────────────────────────────────

    public RouteDto acquireLock(UUID orgId, UUID id) {
        Route route = require(orgId, id);
        String me = lockGuard.currentSubject();
        if (route.getLockHolder() != null && !route.getLockHolder().equals(me)) {
            throw new RoutingConflictException(
                    "Route is locked by another user (" + route.getLockHolder() + ")");
        }
        lockGuard.acquireFor(route);
        return toDto(routes.save(route));
    }

    public RouteDto releaseLock(UUID orgId, UUID id) {
        Route route = require(orgId, id);
        String me = lockGuard.currentSubject();
        if (route.getLockHolder() != null && !route.getLockHolder().equals(me)) {
            throw new RoutingConflictException(
                    "Only the lock holder can unlock this route; a privileged user can force-unlock");
        }
        lockGuard.release(route);
        return toDto(routes.save(route));
    }

    /** Force-unlock regardless of holder; caller privilege (routing:route:unlock) enforced at the API. */
    public RouteDto forceUnlock(UUID orgId, UUID id) {
        Route route = require(orgId, id);
        lockGuard.release(route);
        return toDto(routes.save(route));
    }

    @Transactional(readOnly = true)
    public RouteDto get(UUID orgId, UUID id) {
        return toDto(require(orgId, id));
    }

    @Transactional(readOnly = true)
    public Page<RouteDto> list(UUID orgId, String search, Pageable pageable) {
        String normalised = (search == null || search.isBlank()) ? null : search;
        Page<Route> page = (normalised == null)
                ? routes.findByOrgId(orgId, pageable)
                : routes.search(orgId, normalised, pageable);
        return page.map(RouteService::toDto);
    }

    public RouteDto patch(UUID orgId, UUID id, PatchRouteRequest req) {
        Route route = require(orgId, id);
        requireEditable(route);
        if (req.reasonForRevision() != null) {
            route.setReasonForRevision(req.reasonForRevision());
        }
        if (req.bomId() != null) {
            route.setBomId(req.bomId());
        }
        if (req.bomRevisionId() != null) {
            route.setBomRevisionId(req.bomRevisionId());
        }
        if (req.inspectionPlanRevisionId() != null) {
            route.setInspectionPlanRevisionId(req.inspectionPlanRevisionId());
        }
        if (req.customFields() != null) {
            route.setCustomFields(req.customFields());
        }
        return toDto(routes.save(route));
    }

    public void cancelDraft(UUID orgId, UUID id) {
        Route route = require(orgId, id);
        requireEditable(route);
        routes.delete(route);
    }

    Route require(UUID orgId, UUID id) {
        return routes.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new RoutingNotFoundException("Route not found: " + id));
    }

    /** Editable ⇔ route is DRAFT and the caller holds the edit lock (FR-025 + FR-031/032). */
    private void requireEditable(Route route) {
        if (route.getStatus() != RouteStatus.DRAFT) {
            throw new RoutingConflictException(
                    "Route is not editable in status " + route.getStatus() + "; start a revision");
        }
        lockGuard.requireHeld(route);
    }

    static RouteDto toDto(Route r) {
        return new RouteDto(r.getId(), r.getPartId(), r.getPartRevision(), r.getRouteTypeId(),
                r.getBomId(), r.getBomRevisionId(), r.getInspectionPlanRevisionId(), r.getRevision(),
                r.getStatus().name(), r.getReasonForRevision(), r.isBomRevisionSuperseded(),
                r.isInspectionPlanRevisionSuperseded(), r.getCustomFields(),
                r.getLockHolder(), r.getLockedAt());
    }
}
