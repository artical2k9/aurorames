package com.mes.engineering.workinstruction.api;

import com.mes.common.security.annotation.RequiresPrivilege;
import com.mes.engineering.workinstruction.api.dto.ApproveRevisionRequest;
import com.mes.engineering.workinstruction.api.dto.CreateWorkInstructionRequest;
import com.mes.engineering.workinstruction.api.dto.PatchWorkInstructionHeaderRequest;
import com.mes.engineering.workinstruction.api.dto.RejectRevisionRequest;
import com.mes.engineering.workinstruction.api.dto.ReorderStepsRequest;
import com.mes.engineering.workinstruction.api.dto.StepDto;
import com.mes.engineering.workinstruction.api.dto.StepRequest;
import com.mes.engineering.workinstruction.api.dto.WorkInstructionDto;
import com.mes.engineering.workinstruction.api.dto.WorkInstructionRevisionSummaryDto;
import com.mes.engineering.workinstruction.api.dto.WorkInstructionSummaryDto;
import com.mes.engineering.workinstruction.domain.RevisionStatus;
import com.mes.engineering.workinstruction.domain.WorkInstructionRevision;
import com.mes.engineering.workinstruction.service.StepService;
import com.mes.engineering.workinstruction.service.WorkInstructionService;
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
@RequestMapping("/api/v1/work-instructions")
public class WorkInstructionController {

    private final WorkInstructionService wiService;
    private final StepService stepService;

    public WorkInstructionController(WorkInstructionService wiService, StepService stepService) {
        this.wiService = wiService;
        this.stepService = stepService;
    }

    // ── Header / list ───────────────────────────────────────────────────────

    @GetMapping
    @RequiresPrivilege("engineering:work-instruction:read")
    public Page<WorkInstructionSummaryDto> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return wiService.list(JwtClaimsExtractor.orgId(jwt), search, PageRequest.of(page, size));
    }

    @GetMapping("/identifier-suggestion")
    @RequiresPrivilege("engineering:work-instruction:read")
    public ResponseEntity<String> suggestIdentifier(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String prefix) {
        return ResponseEntity.ok(wiService.suggestIdentifier(JwtClaimsExtractor.orgId(jwt), prefix));
    }

    @PostMapping
    @RequiresPrivilege("engineering:work-instruction:create")
    public ResponseEntity<WorkInstructionDto> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateWorkInstructionRequest request) {
        UUID orgId = JwtClaimsExtractor.orgId(jwt);
        WorkInstructionDto dto = wiService.create(orgId, request, JwtClaimsExtractor.nullSafeSubject(jwt));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(dto.id()).toUri();
        return ResponseEntity.created(location).body(dto);
    }

    @GetMapping("/{id}")
    @RequiresPrivilege("engineering:work-instruction:read")
    public WorkInstructionDto get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestParam(required = false) Integer revisionNumber,
            @RequestParam(required = false) RevisionStatus revisionStatus) {
        return wiService.get(JwtClaimsExtractor.orgId(jwt), id, revisionNumber, revisionStatus);
    }

    @GetMapping("/{id}/revisions")
    @RequiresPrivilege("engineering:work-instruction:read")
    public List<WorkInstructionRevisionSummaryDto> revisions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        return wiService.listRevisions(JwtClaimsExtractor.orgId(jwt), id);
    }

    @PatchMapping("/{id}")
    @RequiresPrivilege("engineering:work-instruction:update")
    public WorkInstructionDto patchHeader(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody PatchWorkInstructionHeaderRequest request) {
        return wiService.patchHeader(JwtClaimsExtractor.orgId(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @RequiresPrivilege("engineering:work-instruction:delete")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        wiService.delete(JwtClaimsExtractor.orgId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    // ── Steps (operate on the editable DRAFT revision) ──────────────────────

    @PostMapping("/{id}/steps")
    @RequiresPrivilege("engineering:work-instruction:update")
    public ResponseEntity<StepDto> addStep(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody StepRequest request) {
        WorkInstructionRevision draft = wiService.requireEditableDraft(JwtClaimsExtractor.orgId(jwt), id);
        return ResponseEntity.status(201).body(stepService.addStep(draft, request));
    }

    @PatchMapping("/{id}/steps/{stepId}")
    @RequiresPrivilege("engineering:work-instruction:update")
    public StepDto updateStep(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @PathVariable UUID stepId,
            @Valid @RequestBody StepRequest request) {
        WorkInstructionRevision draft = wiService.requireEditableDraft(JwtClaimsExtractor.orgId(jwt), id);
        return stepService.updateStep(draft, stepId, request);
    }

    @DeleteMapping("/{id}/steps/{stepId}")
    @RequiresPrivilege("engineering:work-instruction:update")
    public ResponseEntity<Void> deleteStep(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @PathVariable UUID stepId) {
        WorkInstructionRevision draft = wiService.requireEditableDraft(JwtClaimsExtractor.orgId(jwt), id);
        stepService.deleteStep(draft, stepId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/steps/reorder")
    @RequiresPrivilege("engineering:work-instruction:update")
    public List<StepDto> reorderSteps(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ReorderStepsRequest request) {
        WorkInstructionRevision draft = wiService.requireEditableDraft(JwtClaimsExtractor.orgId(jwt), id);
        return stepService.reorder(draft, request.getOrderedStepIds());
    }

    // ── Workflow ────────────────────────────────────────────────────────────

    @PostMapping("/{id}/submit")
    @RequiresPrivilege("engineering:work-instruction:approve")
    public WorkInstructionDto submit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        return wiService.submit(JwtClaimsExtractor.orgId(jwt), id, JwtClaimsExtractor.nullSafeSubject(jwt));
    }

    @PostMapping("/{id}/approve")
    @RequiresPrivilege("engineering:work-instruction:approve")
    public WorkInstructionDto approve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ApproveRevisionRequest request) {
        return wiService.approve(JwtClaimsExtractor.orgId(jwt), id, jwt, request.getPassword());
    }

    @PostMapping("/{id}/reject")
    @RequiresPrivilege("engineering:work-instruction:approve")
    public WorkInstructionDto reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody RejectRevisionRequest request) {
        return wiService.reject(JwtClaimsExtractor.orgId(jwt), id,
                JwtClaimsExtractor.nullSafeSubject(jwt), request.getReason());
    }

    @PostMapping("/{id}/revisions")
    @RequiresPrivilege("engineering:work-instruction:update")
    public WorkInstructionDto createRevision(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        return wiService.createRevision(JwtClaimsExtractor.orgId(jwt), id);
    }

    @PostMapping("/{id}/cancel-draft")
    @RequiresPrivilege("engineering:work-instruction:update")
    public ResponseEntity<Void> cancelDraft(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        wiService.cancelDraft(JwtClaimsExtractor.orgId(jwt), id);
        return ResponseEntity.noContent().build();
    }
}
