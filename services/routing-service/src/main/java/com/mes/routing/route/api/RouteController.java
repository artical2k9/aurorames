package com.mes.routing.route.api;

import com.mes.common.security.annotation.RequiresPrivilege;
import com.mes.routing.route.api.dto.RouteDtos.CreateOperationRequest;
import com.mes.routing.route.api.dto.RouteDtos.CreateRouteRequest;
import com.mes.routing.route.api.dto.RouteDtos.OperationDto;
import com.mes.routing.route.api.dto.RouteDtos.PatchOperationRequest;
import com.mes.routing.route.api.dto.RouteDtos.PatchRouteRequest;
import com.mes.routing.route.api.dto.RouteDtos.RouteDto;
import com.mes.routing.route.service.RouteOperationService;
import com.mes.routing.route.service.RouteService;
import com.mes.udf.api.JwtClaimsExtractor;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Routes (header) and their operations. Reads require routing:route:view; writes routing:route:manage. */
@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {

    private final RouteService routeService;
    private final RouteOperationService operationService;

    public RouteController(RouteService routeService, RouteOperationService operationService) {
        this.routeService = routeService;
        this.operationService = operationService;
    }

    private static UUID org(Jwt jwt) {
        return JwtClaimsExtractor.orgId(jwt);
    }

    @PostMapping
    @RequiresPrivilege("routing:route:manage")
    public ResponseEntity<RouteDto> create(@AuthenticationPrincipal Jwt jwt,
                                           @Valid @RequestBody CreateRouteRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(routeService.create(org(jwt), req));
    }

    @GetMapping
    @RequiresPrivilege("routing:route:view")
    public Page<RouteDto> list(@AuthenticationPrincipal Jwt jwt,
                               @RequestParam(required = false) String search,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size) {
        return routeService.list(org(jwt), search, PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    @RequiresPrivilege("routing:route:view")
    public RouteDto get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return routeService.get(org(jwt), id);
    }

    @PatchMapping("/{id}")
    @RequiresPrivilege("routing:route:manage")
    public RouteDto patch(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                          @Valid @RequestBody PatchRouteRequest req) {
        return routeService.patch(org(jwt), id, req);
    }

    @DeleteMapping("/{id}/draft")
    @RequiresPrivilege("routing:route:manage")
    public ResponseEntity<Void> cancelDraft(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        routeService.cancelDraft(org(jwt), id);
        return ResponseEntity.noContent().build();
    }

    // ── Operations ───────────────────────────────────────────────────────────

    @GetMapping("/{id}/operations")
    @RequiresPrivilege("routing:route:view")
    public List<OperationDto> listOperations(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return operationService.list(org(jwt), id);
    }

    @PostMapping("/{id}/operations")
    @RequiresPrivilege("routing:route:manage")
    public ResponseEntity<OperationDto> addOperation(@AuthenticationPrincipal Jwt jwt,
                                                     @PathVariable UUID id,
                                                     @Valid @RequestBody CreateOperationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(operationService.add(org(jwt), id, req));
    }

    @PatchMapping("/{id}/operations/{opId}")
    @RequiresPrivilege("routing:route:manage")
    public OperationDto patchOperation(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                       @PathVariable UUID opId,
                                       @Valid @RequestBody PatchOperationRequest req) {
        return operationService.patch(org(jwt), id, opId, req);
    }

    @DeleteMapping("/{id}/operations/{opId}")
    @RequiresPrivilege("routing:route:manage")
    public ResponseEntity<Void> deleteOperation(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                @PathVariable UUID opId) {
        operationService.delete(org(jwt), id, opId);
        return ResponseEntity.noContent().build();
    }
}
