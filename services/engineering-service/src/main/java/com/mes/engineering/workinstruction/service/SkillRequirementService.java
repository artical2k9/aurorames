package com.mes.engineering.workinstruction.service;

import com.mes.engineering.workinstruction.api.dto.SkillRequirementDto;
import com.mes.engineering.workinstruction.client.LabourServiceClient;
import com.mes.engineering.workinstruction.domain.RevisionStatus;
import com.mes.engineering.workinstruction.domain.SkillRequirement;
import com.mes.engineering.workinstruction.domain.WorkInstructionRevision;
import com.mes.engineering.workinstruction.repository.SkillRequirementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Skill-requirement CRUD and copy-on-revision. Mutations are guarded to DRAFT revisions only.
 * Skill code/name are denormalised from labour-service at write time so requirements render
 * without a cross-service call. Standalone: the caller resolves the editable revision.
 */
@Service
@Transactional
public class SkillRequirementService {

    private final SkillRequirementRepository skillRepository;
    private final LabourServiceClient labourClient;

    public SkillRequirementService(SkillRequirementRepository skillRepository,
                                   LabourServiceClient labourClient) {
        this.skillRepository = skillRepository;
        this.labourClient = labourClient;
    }

    @Transactional(readOnly = true)
    public List<SkillRequirementDto> listForRevision(UUID revisionId) {
        return skillRepository.findByRevisionIdOrderBySkillCodeAsc(revisionId).stream()
                .map(SkillRequirementService::toDto)
                .toList();
    }

    public SkillRequirementDto add(WorkInstructionRevision draft, UUID skillId) {
        requireDraft(draft);
        if (skillRepository.existsByRevisionIdAndSkillId(draft.getId(), skillId)) {
            throw new WorkInstructionConflictException(
                    "Skill requirement already exists on this revision: " + skillId);
        }
        LabourServiceClient.SkillSummary skill = labourClient.findSkill(skillId)
                .orElseThrow(() -> new WorkInstructionValidationException(
                        "Skill not found in labour-service: " + skillId));

        SkillRequirement req = new SkillRequirement();
        req.setRevision(draft);
        req.setSkillId(skill.id());
        req.setSkillCode(skill.skillCode());
        req.setSkillName(skill.name());
        return toDto(skillRepository.save(req));
    }

    public void remove(WorkInstructionRevision draft, UUID requirementId) {
        requireDraft(draft);
        SkillRequirement req = skillRepository.findByRevisionIdAndId(draft.getId(), requirementId)
                .orElseThrow(() -> new WorkInstructionNotFoundException(
                        "Skill requirement not found on this revision: " + requirementId));
        skillRepository.delete(req);
    }

    /** List the skill ids required by a revision (used by qualification evaluation). */
    @Transactional(readOnly = true)
    public List<UUID> skillIdsForRevision(UUID revisionId) {
        return skillRepository.findByRevisionIdOrderBySkillCodeAsc(revisionId).stream()
                .map(SkillRequirement::getSkillId)
                .toList();
    }

    /** Copy skill-requirement rows from a source revision into a new draft revision. */
    public void copySkillRequirements(UUID sourceRevisionId, WorkInstructionRevision target) {
        for (SkillRequirement src : skillRepository.findByRevisionIdOrderBySkillCodeAsc(sourceRevisionId)) {
            SkillRequirement copy = new SkillRequirement();
            copy.setRevision(target);
            copy.setSkillId(src.getSkillId());
            copy.setSkillCode(src.getSkillCode());
            copy.setSkillName(src.getSkillName());
            skillRepository.save(copy);
        }
    }

    private static void requireDraft(WorkInstructionRevision revision) {
        if (revision.getRevisionStatus() != RevisionStatus.DRAFT) {
            throw new WorkInstructionConflictException(
                    "Skill requirements can only be modified on a DRAFT revision");
        }
    }

    private static SkillRequirementDto toDto(SkillRequirement r) {
        return new SkillRequirementDto(r.getId(), r.getSkillId(), r.getSkillCode(), r.getSkillName());
    }
}
