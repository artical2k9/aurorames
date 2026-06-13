package com.mes.quality.inspectionplan.api;

import com.mes.common.security.annotation.RequiresPrivilege;
import com.mes.quality.inspectionplan.api.dto.CharacteristicDto;
import com.mes.quality.inspectionplan.api.dto.CharacteristicRequest;
import com.mes.quality.inspectionplan.api.dto.CreateInspectionPlanRequest;
import com.mes.quality.inspectionplan.api.dto.InspectionPlanDto;
import com.mes.quality.inspectionplan.api.dto.InspectionPlanRevisionSummaryDto;
import com.mes.quality.inspectionplan.api.dto.InspectionPlanSummaryDto;
import com.mes.quality.inspectionplan.api.dto.PatchInspectionPlanHeaderRequest;
import com.mes.quality.inspectionplan.api.dto.RejectRevisionRequest;
import com.mes.quality.inspectionplan.domain.RevisionStatus;
import com.mes.quality.inspectionplan.service.CharacteristicService;
import com.mes.quality.inspectionplan.service.InspectionPlanService;
import com.mes.udf.api.JwtClaimsExtractor;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inspection-plans")
public class InspectionPlanController {

    private final InspectionPlanService planService;
    private final CharacteristicService characteristicService;

    public InspectionPlanController(InspectionPlanService planService,
                                    CharacteristicService characteristicService) {
        this.planService = planService;
        this.characteristicService = characteristicService;
    }

    @PostMapping
    @RequiresPrivilege("quality:inspection-plan:create")
    public ResponseEntity<InspectionPlanDto> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateInspectionPlanRequest request) {
        InspectionPlanDto dto = planService.createPlan(JwtClaimsExtractor.orgId(jwt), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(dto.id()).toUri();
        return ResponseEntity.created(location).body(dto);
    }

    @GetMapping
    @RequiresPrivilege("quality:inspection-plan:read")
    public Page<InspectionPlanSummaryDto> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return planService.listPlans(JwtClaimsExtractor.orgId(jwt), search, PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    @RequiresPrivilege("quality:inspection-plan:read")
    public InspectionPlanDto get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestParam(required = false) Integer revisionNumber,
            @RequestParam(required = false) RevisionStatus revisionStatus) {
        return planService.getPlan(JwtClaimsExtractor.orgId(jwt), id, revisionNumber, revisionStatus);
    }

    @PatchMapping("/{id}")
    @RequiresPrivilege("quality:inspection-plan:update")
    public InspectionPlanDto patchHeader(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody PatchInspectionPlanHeaderRequest request) {
        return planService.patchHeader(JwtClaimsExtractor.orgId(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @RequiresPrivilege("quality:inspection-plan:delete")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        planService.deletePlan(JwtClaimsExtractor.orgId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/revisions")
    @RequiresPrivilege("quality:inspection-plan:read")
    public List<InspectionPlanRevisionSummaryDto> listRevisions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        return planService.listRevisions(JwtClaimsExtractor.orgId(jwt), id);
    }

    @PostMapping("/{id}/submit")
    @RequiresPrivilege("quality:inspection-plan:update")
    public InspectionPlanDto submit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        return planService.submit(JwtClaimsExtractor.orgId(jwt), id, JwtClaimsExtractor.nullSafeSubject(jwt));
    }

    @PostMapping("/{id}/approve")
    @RequiresPrivilege("quality:inspection-plan:approve")
    public InspectionPlanDto approve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        return planService.approve(JwtClaimsExtractor.orgId(jwt), id, JwtClaimsExtractor.nullSafeSubject(jwt));
    }

    @PostMapping("/{id}/reject")
    @RequiresPrivilege("quality:inspection-plan:approve")
    public InspectionPlanDto reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody RejectRevisionRequest request) {
        return planService.reject(JwtClaimsExtractor.orgId(jwt), id,
                JwtClaimsExtractor.nullSafeSubject(jwt), request.getReason());
    }

    @PostMapping("/{id}/revisions")
    @RequiresPrivilege("quality:inspection-plan:update")
    public InspectionPlanDto createRevision(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        return planService.createRevision(JwtClaimsExtractor.orgId(jwt), id);
    }

    @DeleteMapping("/{id}/draft")
    @RequiresPrivilege("quality:inspection-plan:update")
    public ResponseEntity<Void> cancelDraft(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        planService.cancelDraft(JwtClaimsExtractor.orgId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    // ── Characteristics (DRAFT revision only) ─────────────────────────────────

    @GetMapping("/{id}/characteristics")
    @RequiresPrivilege("quality:inspection-plan:read")
    public List<CharacteristicDto> listCharacteristics(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestParam(required = false) Integer revisionNumber) {
        return characteristicService.list(JwtClaimsExtractor.orgId(jwt), id, revisionNumber);
    }

    @PostMapping("/{id}/characteristics")
    @RequiresPrivilege("quality:inspection-plan:update")
    public ResponseEntity<CharacteristicDto> addCharacteristic(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody CharacteristicRequest request) {
        CharacteristicDto dto = characteristicService.add(JwtClaimsExtractor.orgId(jwt), id, request);
        return ResponseEntity.status(201).body(dto);
    }

    @PatchMapping("/{id}/characteristics/{charId}")
    @RequiresPrivilege("quality:inspection-plan:update")
    public CharacteristicDto updateCharacteristic(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @PathVariable UUID charId,
            @Valid @RequestBody CharacteristicRequest request) {
        return characteristicService.update(JwtClaimsExtractor.orgId(jwt), id, charId, request);
    }

    @DeleteMapping("/{id}/characteristics/{charId}")
    @RequiresPrivilege("quality:inspection-plan:update")
    public ResponseEntity<Void> deleteCharacteristic(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @PathVariable UUID charId) {
        characteristicService.delete(JwtClaimsExtractor.orgId(jwt), id, charId);
        return ResponseEntity.noContent().build();
    }
}
