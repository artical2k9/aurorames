package com.mes.engineering.workinstruction.service;

import com.mes.engineering.workinstruction.api.dto.StepDto;
import com.mes.engineering.workinstruction.api.dto.StepRequest;
import com.mes.engineering.workinstruction.api.dto.WorkInstructionMapper;
import com.mes.engineering.workinstruction.domain.RevisionStatus;
import com.mes.engineering.workinstruction.domain.WorkInstructionRevision;
import com.mes.engineering.workinstruction.domain.WorkInstructionStep;
import com.mes.engineering.workinstruction.repository.WorkInstructionStepRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Step CRUD, reorder, and copy-on-revision. All mutations are guarded to DRAFT revisions only
 * (approved/pending content is immutable). Body HTML is sanitised on every write. Standalone:
 * never depends on WorkInstructionService (the caller resolves the editable revision).
 */
@Service
@Transactional
public class StepService {

    private static final int STEP_INCREMENT = 10;

    private final WorkInstructionStepRepository stepRepository;

    public StepService(WorkInstructionStepRepository stepRepository) {
        this.stepRepository = stepRepository;
    }

    @Transactional(readOnly = true)
    public List<StepDto> stepsOf(UUID revisionId) {
        return stepRepository.findByRevisionIdOrderByStepNumberAsc(revisionId).stream()
                .map(WorkInstructionMapper::toStepDto)
                .toList();
    }

    public StepDto addStep(WorkInstructionRevision draft, StepRequest req) {
        requireDraft(draft);
        int stepNumber = req.getStepNumber() != null ? req.getStepNumber() : nextStepNumber(draft.getId());
        if (stepRepository.findByRevisionIdAndStepNumber(draft.getId(), stepNumber).isPresent()) {
            throw new WorkInstructionConflictException(
                    "Step number already exists in this revision: " + stepNumber);
        }
        WorkInstructionStep step = new WorkInstructionStep();
        step.setRevision(draft);
        step.setStepNumber(stepNumber);
        step.setTitle(req.getTitle());
        step.setBodyHtml(HtmlSanitiser.sanitise(req.getBodyHtml()));
        step.setCustomFields(req.getCustomFields());
        return WorkInstructionMapper.toStepDto(stepRepository.save(step));
    }

    public StepDto updateStep(WorkInstructionRevision draft, UUID stepId, StepRequest req) {
        requireDraft(draft);
        WorkInstructionStep step = requireStep(draft.getId(), stepId);
        step.setTitle(req.getTitle());
        step.setBodyHtml(HtmlSanitiser.sanitise(req.getBodyHtml()));
        if (req.getCustomFields() != null) {
            step.setCustomFields(req.getCustomFields());
        }
        return WorkInstructionMapper.toStepDto(stepRepository.save(step));
    }

    public void deleteStep(WorkInstructionRevision draft, UUID stepId) {
        requireDraft(draft);
        WorkInstructionStep step = requireStep(draft.getId(), stepId);
        stepRepository.delete(step);
    }

    /** Reassign step_number to 10, 20, 30, ... following the supplied id order. */
    public List<StepDto> reorder(WorkInstructionRevision draft, List<UUID> orderedStepIds) {
        requireDraft(draft);
        List<WorkInstructionStep> existing =
                stepRepository.findByRevisionIdOrderByStepNumberAsc(draft.getId());
        if (orderedStepIds.size() != existing.size()
                || !orderedStepIds.stream().allMatch(id ->
                        existing.stream().anyMatch(s -> s.getId().equals(id)))) {
            throw new WorkInstructionValidationException(
                    "Reorder list must contain exactly the current step ids of this revision");
        }
        // Two-phase to avoid tripping the unique (revision, step_number) constraint mid-update.
        int offset = (existing.size() + 1) * STEP_INCREMENT;
        for (int i = 0; i < orderedStepIds.size(); i++) {
            WorkInstructionStep step = byId(existing, orderedStepIds.get(i));
            step.setStepNumber(offset + i);
        }
        stepRepository.saveAllAndFlush(existing);
        for (int i = 0; i < orderedStepIds.size(); i++) {
            byId(existing, orderedStepIds.get(i)).setStepNumber((i + 1) * STEP_INCREMENT);
        }
        stepRepository.saveAllAndFlush(existing);
        return stepsOf(draft.getId());
    }

    /** Copy all steps from a source revision into a new draft revision (copy-on-revision). */
    public void copySteps(UUID sourceRevisionId, WorkInstructionRevision target) {
        for (WorkInstructionStep src : stepRepository.findByRevisionIdOrderByStepNumberAsc(sourceRevisionId)) {
            WorkInstructionStep copy = new WorkInstructionStep();
            copy.setRevision(target);
            copy.setStepNumber(src.getStepNumber());
            copy.setTitle(src.getTitle());
            copy.setBodyHtml(src.getBodyHtml());
            copy.setCustomFields(src.getCustomFields());
            stepRepository.save(copy);
        }
    }

    public long countSteps(UUID revisionId) {
        return stepRepository.countByRevisionId(revisionId);
    }

    private int nextStepNumber(UUID revisionId) {
        return stepRepository.findByRevisionIdOrderByStepNumberAsc(revisionId).stream()
                .mapToInt(WorkInstructionStep::getStepNumber)
                .max()
                .orElse(0) + STEP_INCREMENT;
    }

    private WorkInstructionStep requireStep(UUID revisionId, UUID stepId) {
        return stepRepository.findByRevisionIdAndId(revisionId, stepId)
                .orElseThrow(() -> new WorkInstructionNotFoundException(
                        "Step not found in this revision: " + stepId));
    }

    private static WorkInstructionStep byId(List<WorkInstructionStep> steps, UUID id) {
        return steps.stream().filter(s -> s.getId().equals(id)).findFirst().orElseThrow();
    }

    private static void requireDraft(WorkInstructionRevision revision) {
        if (revision.getRevisionStatus() != RevisionStatus.DRAFT) {
            throw new WorkInstructionConflictException(
                    "Steps can only be modified on a DRAFT revision");
        }
    }
}
