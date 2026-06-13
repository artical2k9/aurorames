package com.mes.quality.inspectionplan.api;

import com.mes.common.security.annotation.RequiresPrivilege;
import com.mes.quality.inspectionplan.api.dto.ApprovedPlanDto;
import com.mes.quality.inspectionplan.api.dto.PlanStatusDto;
import com.mes.quality.inspectionplan.service.InspectionPlanService;
import com.mes.udf.api.JwtClaimsExtractor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Read-only consumer contract for MES-9 (route creation / work-order release gating).
 * Resolves by item id; org scoping from the JWT claim.
 */
@RestController
@RequestMapping("/api/v1/inspection-plans/by-item")
public class ConsumerController {

    private final InspectionPlanService planService;

    public ConsumerController(InspectionPlanService planService) {
        this.planService = planService;
    }

    @GetMapping("/{itemId}/approved")
    @RequiresPrivilege("quality:inspection-plan:read")
    public ApprovedPlanDto approvedByItem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID itemId) {
        return planService.getApprovedByItem(JwtClaimsExtractor.orgId(jwt), itemId);
    }

    @GetMapping("/{itemId}/status")
    @RequiresPrivilege("quality:inspection-plan:read")
    public PlanStatusDto statusByItem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID itemId) {
        return planService.statusByItem(JwtClaimsExtractor.orgId(jwt), itemId);
    }
}
