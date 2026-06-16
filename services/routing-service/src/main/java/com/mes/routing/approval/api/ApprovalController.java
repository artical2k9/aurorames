package com.mes.routing.approval.api;

import com.mes.common.security.annotation.RequiresPrivilege;
import com.mes.routing.approval.api.dto.ApprovalDtos.ApprovalHistoryDto;
import com.mes.routing.approval.api.dto.ApprovalDtos.ApprovalRecordDto;
import com.mes.routing.approval.api.dto.ApprovalDtos.ApproveRequest;
import com.mes.routing.approval.api.dto.ApprovalDtos.RejectRequest;
import com.mes.routing.approval.api.dto.ApprovalDtos.RouteApprovalStatusDto;
import com.mes.routing.approval.service.ApprovalService;
import com.mes.udf.api.JwtClaimsExtractor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Route/operation approval via e-signature and the revision audit history (US7/US8). */
@RestController
@RequestMapping("/api/v1/routes/{routeId}")
public class ApprovalController {

    private final ApprovalService service;

    public ApprovalController(ApprovalService service) {
        this.service = service;
    }

    private static UUID org(Jwt jwt) {
        return JwtClaimsExtractor.orgId(jwt);
    }

    @PostMapping("/submit")
    @RequiresPrivilege("routing:route:manage")
    public RouteApprovalStatusDto submit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID routeId) {
        return service.submit(org(jwt), routeId);
    }

    @PostMapping("/approve")
    @RequiresPrivilege("routing:route:approve")
    public RouteApprovalStatusDto approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID routeId,
                                          @Valid @RequestBody ApproveRequest req) {
        return service.approve(org(jwt), routeId, jwt, req);
    }

    @PostMapping("/reject")
    @RequiresPrivilege("routing:route:approve")
    public RouteApprovalStatusDto reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID routeId,
                                         @Valid @RequestBody RejectRequest req) {
        return service.reject(org(jwt), routeId, jwt, req);
    }

    @GetMapping("/approval-status")
    @RequiresPrivilege("routing:route:view")
    public RouteApprovalStatusDto status(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID routeId) {
        return service.status(org(jwt), routeId);
    }

    @GetMapping("/approval-history")
    @RequiresPrivilege("routing:route:view")
    public ApprovalHistoryDto history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID routeId) {
        return service.history(org(jwt), routeId);
    }

    @PostMapping("/operations/{opId}/approve")
    @RequiresPrivilege("routing:operation:approve")
    public ResponseEntity<ApprovalRecordDto> approveOperation(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID routeId, @PathVariable UUID opId, @Valid @RequestBody ApproveRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.approveOperation(org(jwt), routeId, opId, jwt, req));
    }
}
