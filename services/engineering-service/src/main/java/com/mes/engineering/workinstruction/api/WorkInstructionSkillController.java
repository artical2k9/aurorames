package com.mes.engineering.workinstruction.api;

import com.mes.common.security.annotation.RequiresPrivilege;
import com.mes.engineering.workinstruction.api.dto.AddSkillRequirementRequest;
import com.mes.engineering.workinstruction.api.dto.QualificationResultDto;
import com.mes.engineering.workinstruction.api.dto.SkillRequirementDto;
import com.mes.engineering.workinstruction.domain.WorkInstructionRevision;
import com.mes.engineering.workinstruction.service.QualificationService;
import com.mes.engineering.workinstruction.service.SkillRequirementService;
import com.mes.engineering.workinstruction.service.WorkInstructionService;
import com.mes.udf.api.JwtClaimsExtractor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Skill requirements and on-demand qualification evaluation for a work instruction.
 * Mutations target the editable DRAFT revision; reads target the display revision (or an
 * explicit {@code revisionNumber}).
 */
@RestController
@RequestMapping("/api/v1/work-instructions/{id}")
public class WorkInstructionSkillController {

    private final WorkInstructionService wiService;
    private final SkillRequirementService skillRequirementService;
    private final QualificationService qualificationService;

    public WorkInstructionSkillController(WorkInstructionService wiService,
                                          SkillRequirementService skillRequirementService,
                                          QualificationService qualificationService) {
        this.wiService = wiService;
        this.skillRequirementService = skillRequirementService;
        this.qualificationService = qualificationService;
    }

    @GetMapping("/skill-requirements")
    @RequiresPrivilege("engineering:work-instruction:read")
    public List<SkillRequirementDto> listSkillRequirements(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestParam(required = false) Integer revisionNumber) {
        WorkInstructionRevision revision =
                wiService.resolveReadRevision(JwtClaimsExtractor.orgId(jwt), id, revisionNumber);
        return skillRequirementService.listForRevision(revision.getId());
    }

    @PostMapping("/skill-requirements")
    @RequiresPrivilege("engineering:work-instruction:update")
    public ResponseEntity<SkillRequirementDto> addSkillRequirement(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody AddSkillRequirementRequest request) {
        WorkInstructionRevision draft =
                wiService.requireEditableDraft(JwtClaimsExtractor.orgId(jwt), id);
        return ResponseEntity.status(201)
                .body(skillRequirementService.add(draft, request.getSkillId()));
    }

    @DeleteMapping("/skill-requirements/{reqId}")
    @RequiresPrivilege("engineering:work-instruction:update")
    public ResponseEntity<Void> removeSkillRequirement(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @PathVariable UUID reqId) {
        WorkInstructionRevision draft =
                wiService.requireEditableDraft(JwtClaimsExtractor.orgId(jwt), id);
        skillRequirementService.remove(draft, reqId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/qualification")
    @RequiresPrivilege("engineering:work-instruction:read")
    public QualificationResultDto evaluateQualification(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) String iamUserId,
            @RequestParam(required = false) Integer revisionNumber) {
        WorkInstructionRevision revision =
                wiService.resolveReadRevision(JwtClaimsExtractor.orgId(jwt), id, revisionNumber);
        return qualificationService.evaluate(revision, employeeId, iamUserId);
    }
}
