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

    public RouteService(RouteRepository routes, RouteTypeRepository routeTypes) {
        this.routes = routes;
        this.routeTypes = routeTypes;
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
        route.setRevision(1);
        route.setStatus(RouteStatus.DRAFT);
        route.setCustomFields(req.customFields());
        return toDto(routes.save(route));
    }

    @Transactional(readOnly = true)
    public RouteDto get(UUID orgId, UUID id) {
        return toDto(require(orgId, id));
    }

    @Transactional(readOnly = true)
    public Page<RouteDto> list(UUID orgId, String search, Pageable pageable) {
        String normalised = (search == null || search.isBlank()) ? null : search;
        return routes.search(orgId, normalised, pageable).map(RouteService::toDto);
    }

    public RouteDto patch(UUID orgId, UUID id, PatchRouteRequest req) {
        Route route = require(orgId, id);
        requireDraft(route);
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
        requireDraft(route);
        routes.delete(route);
    }

    Route require(UUID orgId, UUID id) {
        return routes.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new RoutingNotFoundException("Route not found: " + id));
    }

    private static void requireDraft(Route route) {
        if (route.getStatus() != RouteStatus.DRAFT) {
            throw new RoutingConflictException(
                    "Route is not editable in status " + route.getStatus() + "; start a revision");
        }
    }

    static RouteDto toDto(Route r) {
        return new RouteDto(r.getId(), r.getPartId(), r.getPartRevision(), r.getRouteTypeId(),
                r.getBomId(), r.getBomRevisionId(), r.getInspectionPlanRevisionId(), r.getRevision(),
                r.getStatus().name(), r.getReasonForRevision(), r.isBomRevisionSuperseded(),
                r.isInspectionPlanRevisionSuperseded(), r.getCustomFields());
    }
}
