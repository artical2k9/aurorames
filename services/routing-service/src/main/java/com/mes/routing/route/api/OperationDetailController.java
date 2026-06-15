package com.mes.routing.route.api;

import com.mes.common.security.annotation.RequiresPrivilege;
import com.mes.routing.route.api.dto.OperationDetailDtos.LabourPlanLineDto;
import com.mes.routing.route.api.dto.OperationDetailDtos.MaterialConsumptionDto;
import com.mes.routing.route.api.dto.OperationDetailDtos.OperationResourceDto;
import com.mes.routing.route.api.dto.OperationDetailDtos.QualityVariableDto;
import com.mes.routing.route.api.dto.OperationDetailDtos.SkillRequirementDto;
import com.mes.routing.route.api.dto.OperationDetailDtos.StepFileReferenceDto;
import com.mes.routing.route.api.dto.OperationDetailDtos.ToolingRequirementDto;
import com.mes.routing.route.api.dto.OperationDetailDtos.WorkInstructionLinkDto;
import com.mes.routing.route.service.OperationDetailService;
import com.mes.udf.api.JwtClaimsExtractor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Operation-detail child resources (US3). Reads routing:route:view; writes routing:route:manage. */
@RestController
@RequestMapping("/api/v1/routes/{routeId}/operations/{opId}")
public class OperationDetailController {

    private final OperationDetailService service;

    public OperationDetailController(OperationDetailService service) {
        this.service = service;
    }

    private static UUID org(Jwt jwt) {
        return JwtClaimsExtractor.orgId(jwt);
    }

    private static <T> ResponseEntity<T> created(T body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    // ── Resources ──────────────────────────────────────────────────────────────
    @GetMapping("/resources")
    @RequiresPrivilege("routing:route:view")
    public List<OperationResourceDto> listResources(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID routeId, @PathVariable UUID opId) {
        return service.listResources(org(jwt), routeId, opId);
    }

    @PostMapping("/resources")
    @RequiresPrivilege("routing:route:manage")
    public ResponseEntity<OperationResourceDto> addResource(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID routeId, @PathVariable UUID opId, @Valid @RequestBody OperationResourceDto r) {
        return created(service.addResource(org(jwt), routeId, opId, r));
    }

    @DeleteMapping("/resources/{id}")
    @RequiresPrivilege("routing:route:manage")
    public ResponseEntity<Void> deleteResource(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID routeId, @PathVariable UUID opId, @PathVariable UUID id) {
        service.deleteResource(org(jwt), routeId, opId, id);
        return ResponseEntity.noContent().build();
    }

    // ── Labour plan ──────────────────────────────────────────────────────────────
    @GetMapping("/labour-plan")
    @RequiresPrivilege("routing:route:view")
    public List<LabourPlanLineDto> listLabourPlan(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID routeId, @PathVariable UUID opId) {
        return service.listLabourPlan(org(jwt), routeId, opId);
    }

    @PostMapping("/labour-plan")
    @RequiresPrivilege("routing:route:manage")
    public ResponseEntity<LabourPlanLineDto> addLabourPlan(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID routeId, @PathVariable UUID opId, @Valid @RequestBody LabourPlanLineDto r) {
        return created(service.addLabourPlan(org(jwt), routeId, opId, r));
    }

    @DeleteMapping("/labour-plan/{id}")
    @RequiresPrivilege("routing:route:manage")
    public ResponseEntity<Void> deleteLabourPlan(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID routeId, @PathVariable UUID opId, @PathVariable UUID id) {
        service.deleteLabourPlan(org(jwt), routeId, opId, id);
        return ResponseEntity.noContent().build();
    }

    // ── Materials ────────────────────────────────────────────────────────────────
    @GetMapping("/materials")
    @RequiresPrivilege("routing:route:view")
    public List<MaterialConsumptionDto> listMaterials(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID routeId, @PathVariable UUID opId) {
        return service.listMaterials(org(jwt), routeId, opId);
    }

    @PostMapping("/materials")
    @RequiresPrivilege("routing:route:manage")
    public ResponseEntity<MaterialConsumptionDto> addMaterial(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID routeId, @PathVariable UUID opId, @Valid @RequestBody MaterialConsumptionDto r) {
        return created(service.addMaterial(org(jwt), routeId, opId, r));
    }

    @DeleteMapping("/materials/{id}")
    @RequiresPrivilege("routing:route:manage")
    public ResponseEntity<Void> deleteMaterial(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID routeId, @PathVariable UUID opId, @PathVariable UUID id) {
        service.deleteMaterial(org(jwt), routeId, opId, id);
        return ResponseEntity.noContent().build();
    }

    // ── Quality variables ─────────────────────────────────────────────────────────
    @GetMapping("/quality-variables")
    @RequiresPrivilege("routing:route:view")
    public List<QualityVariableDto> listQualityVariables(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID routeId, @PathVariable UUID opId) {
        return service.listQualityVariables(org(jwt), routeId, opId);
    }

    @PostMapping("/quality-variables")
    @RequiresPrivilege("routing:route:manage")
    public ResponseEntity<QualityVariableDto> addQualityVariable(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID routeId, @PathVariable UUID opId, @Valid @RequestBody QualityVariableDto r) {
        return created(service.addQualityVariable(org(jwt), routeId, opId, r));
    }

    @DeleteMapping("/quality-variables/{id}")
    @RequiresPrivilege("routing:route:manage")
    public ResponseEntity<Void> deleteQualityVariable(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID routeId, @PathVariable UUID opId, @PathVariable UUID id) {
        service.deleteQualityVariable(org(jwt), routeId, opId, id);
        return ResponseEntity.noContent().build();
    }

    // ── Tooling ────────────────────────────────────────────────────────────────────
    @GetMapping("/tooling")
    @RequiresPrivilege("routing:route:view")
    public List<ToolingRequirementDto> listTooling(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID routeId, @PathVariable UUID opId) {
        return service.listTooling(org(jwt), routeId, opId);
    }

    @PostMapping("/tooling")
    @RequiresPrivilege("routing:route:manage")
    public ResponseEntity<ToolingRequirementDto> addTooling(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID routeId, @PathVariable UUID opId, @Valid @RequestBody ToolingRequirementDto r) {
        return created(service.addTooling(org(jwt), routeId, opId, r));
    }

    @DeleteMapping("/tooling/{id}")
    @RequiresPrivilege("routing:route:manage")
    public ResponseEntity<Void> deleteTooling(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID routeId, @PathVariable UUID opId, @PathVariable UUID id) {
        service.deleteTooling(org(jwt), routeId, opId, id);
        return ResponseEntity.noContent().build();
    }

    // ── Skills ───────────────────────────────────────────────────────────────────
    @GetMapping("/skills")
    @RequiresPrivilege("routing:route:view")
    public List<SkillRequirementDto> listSkills(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID routeId, @PathVariable UUID opId) {
        return service.listSkills(org(jwt), routeId, opId);
    }

    @PostMapping("/skills")
    @RequiresPrivilege("routing:route:manage")
    public ResponseEntity<SkillRequirementDto> addSkill(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID routeId, @PathVariable UUID opId, @Valid @RequestBody SkillRequirementDto r) {
        return created(service.addSkill(org(jwt), routeId, opId, r));
    }

    @DeleteMapping("/skills/{id}")
    @RequiresPrivilege("routing:route:manage")
    public ResponseEntity<Void> deleteSkill(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID routeId, @PathVariable UUID opId, @PathVariable UUID id) {
        service.deleteSkill(org(jwt), routeId, opId, id);
        return ResponseEntity.noContent().build();
    }

    // ── Work-instruction link ──────────────────────────────────────────────────────
    @GetMapping("/work-instruction")
    @RequiresPrivilege("routing:route:view")
    public List<WorkInstructionLinkDto> listWorkInstructions(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID routeId, @PathVariable UUID opId) {
        return service.listWorkInstructions(org(jwt), routeId, opId);
    }

    @PostMapping("/work-instruction")
    @RequiresPrivilege("routing:route:manage")
    public ResponseEntity<WorkInstructionLinkDto> addWorkInstruction(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID routeId, @PathVariable UUID opId, @Valid @RequestBody WorkInstructionLinkDto r) {
        return created(service.addWorkInstruction(org(jwt), routeId, opId, r));
    }

    @DeleteMapping("/work-instruction/{id}")
    @RequiresPrivilege("routing:route:manage")
    public ResponseEntity<Void> deleteWorkInstruction(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID routeId, @PathVariable UUID opId, @PathVariable UUID id) {
        service.deleteWorkInstruction(org(jwt), routeId, opId, id);
        return ResponseEntity.noContent().build();
    }

    // ── STEP-file reference ────────────────────────────────────────────────────────
    @GetMapping("/step-file")
    @RequiresPrivilege("routing:route:view")
    public List<StepFileReferenceDto> listStepFiles(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID routeId, @PathVariable UUID opId) {
        return service.listStepFiles(org(jwt), routeId, opId);
    }

    @PostMapping("/step-file")
    @RequiresPrivilege("routing:route:manage")
    public ResponseEntity<StepFileReferenceDto> addStepFile(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID routeId, @PathVariable UUID opId, @Valid @RequestBody StepFileReferenceDto r) {
        return created(service.addStepFile(org(jwt), routeId, opId, r));
    }

    @DeleteMapping("/step-file/{id}")
    @RequiresPrivilege("routing:route:manage")
    public ResponseEntity<Void> deleteStepFile(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID routeId, @PathVariable UUID opId, @PathVariable UUID id) {
        service.deleteStepFile(org(jwt), routeId, opId, id);
        return ResponseEntity.noContent().build();
    }
}
