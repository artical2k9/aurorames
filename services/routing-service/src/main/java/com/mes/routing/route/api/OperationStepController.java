package com.mes.routing.route.api;

import com.mes.common.security.annotation.RequiresPrivilege;
import com.mes.routing.route.api.dto.FlowDtos.CreateStepRequest;
import com.mes.routing.route.api.dto.FlowDtos.PatchStepRequest;
import com.mes.routing.route.api.dto.FlowDtos.StepDto;
import com.mes.routing.route.service.OperationFlowService;
import com.mes.udf.api.JwtClaimsExtractor;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Operation steps (US6). Reads require routing:route:view; writes routing:route:manage. */
@RestController
@RequestMapping("/api/v1/routes/{routeId}/operations/{opId}/steps")
public class OperationStepController {

    private final OperationFlowService service;

    public OperationStepController(OperationFlowService service) {
        this.service = service;
    }

    private static UUID org(Jwt jwt) {
        return JwtClaimsExtractor.orgId(jwt);
    }

    @GetMapping
    @RequiresPrivilege("routing:route:view")
    public List<StepDto> list(@AuthenticationPrincipal Jwt jwt,
                              @PathVariable UUID routeId, @PathVariable UUID opId) {
        return service.listSteps(org(jwt), routeId, opId);
    }

    @PostMapping
    @RequiresPrivilege("routing:route:manage")
    public ResponseEntity<StepDto> add(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID routeId, @PathVariable UUID opId, @Valid @RequestBody CreateStepRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addStep(org(jwt), routeId, opId, req));
    }

    @PatchMapping("/{stepId}")
    @RequiresPrivilege("routing:route:manage")
    public StepDto patch(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID routeId,
            @PathVariable UUID opId, @PathVariable UUID stepId, @Valid @RequestBody PatchStepRequest req) {
        return service.patchStep(org(jwt), routeId, opId, stepId, req);
    }

    @DeleteMapping("/{stepId}")
    @RequiresPrivilege("routing:route:manage")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID routeId,
            @PathVariable UUID opId, @PathVariable UUID stepId) {
        service.deleteStep(org(jwt), routeId, opId, stepId);
        return ResponseEntity.noContent().build();
    }
}
