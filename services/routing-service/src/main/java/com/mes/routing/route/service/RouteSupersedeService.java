package com.mes.routing.route.service;

import com.mes.routing.route.domain.Route;
import com.mes.routing.route.repository.RouteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Marks routes whose pinned BOM/inspection-plan revision has been superseded upstream, driving
 * the non-blocking "newer upstream revision available" indicator (FR-004f). Idempotent: setting
 * an already-set flag is a no-op, so event re-delivery is safe (§VIII). Never alters the pinned
 * revision or the route status.
 */
@Service
@Transactional
public class RouteSupersedeService {

    private final RouteRepository routes;

    public RouteSupersedeService(RouteRepository routes) {
        this.routes = routes;
    }

    public void markBomRevisionSuperseded(UUID bomRevisionId) {
        for (Route route : routes.findByBomRevisionId(bomRevisionId)) {
            if (!route.isBomRevisionSuperseded()) {
                route.setBomRevisionSuperseded(true);
                routes.save(route);
            }
        }
    }

    public void markInspectionPlanRevisionSuperseded(UUID inspectionPlanRevisionId) {
        for (Route route : routes.findByInspectionPlanRevisionId(inspectionPlanRevisionId)) {
            if (!route.isInspectionPlanRevisionSuperseded()) {
                route.setInspectionPlanRevisionSuperseded(true);
                routes.save(route);
            }
        }
    }
}
