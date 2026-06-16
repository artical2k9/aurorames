package com.mes.routing.route.api;

import com.mes.common.security.annotation.RequiresPrivilege;
import com.mes.routing.route.api.dto.RevisionDtos.PatchOperationContentRequest;
import com.mes.routing.route.api.dto.RevisionDtos.StartRevisionRequest;
import com.mes.routing.route.api.dto.RouteDtos.OperationDto;
import com.mes.routing.route.api.dto.RouteDtos.RouteDto;
import com.mes.routing.route.service.RouteRevisionService;
import com.mes.udf.api.JwtClaimsExtractor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Two-tier revisioning entry points (US8). All require routing:route:manage. */
@RestController
@RequestMapping("/api/v1/routes/{routeId}")
public class RevisionController {

    private final RouteRevisionService service;

    public RevisionController(RouteRevisionService service) {
        this.service = service;
    }

    private static UUID org(Jwt jwt) {
        return JwtClaimsExtractor.orgId(jwt);
    }

    @PostMapping("/revisions")
    @RequiresPrivilege("routing:route:manage")
    public ResponseEntity<RouteDto> startRouteRevision(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID routeId, @Valid @RequestBody(required = false) StartRevisionRequest req) {
        String reason = req != null ? req.reasonForRevision() : null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.startRouteRevision(org(jwt), routeId, reason));
    }

    @PostMapping("/operations/{opId}/revision")
    @RequiresPrivilege("routing:route:manage")
    public ResponseEntity<OperationDto> startOperationRevision(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID routeId, @PathVariable UUID opId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.startOperationRevision(org(jwt), routeId, opId));
    }

    @PatchMapping("/operations/{opId}/content")
    @RequiresPrivilege("routing:route:manage")
    public OperationDto patchOperationContent(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID routeId,
            @PathVariable UUID opId, @Valid @RequestBody PatchOperationContentRequest req) {
        return service.patchOperationContent(org(jwt), routeId, opId, req);
    }

    @PostMapping("/operations/{opId}/submit")
    @RequiresPrivilege("routing:route:manage")
    public OperationDto submitOperation(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID routeId,
            @PathVariable UUID opId) {
        return service.submitOperation(org(jwt), routeId, opId);
    }
}
