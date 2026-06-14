package com.mes.engineering.workinstruction.service;

import com.mes.engineering.workinstruction.api.dto.CreateWorkInstructionRequest;
import com.mes.engineering.workinstruction.api.dto.PatchWorkInstructionHeaderRequest;
import com.mes.engineering.workinstruction.api.dto.SignatureDto;
import com.mes.engineering.workinstruction.api.dto.StepDto;
import com.mes.engineering.workinstruction.api.dto.WorkInstructionDto;
import com.mes.engineering.workinstruction.api.dto.WorkInstructionMapper;
import com.mes.engineering.workinstruction.api.dto.WorkInstructionRevisionSummaryDto;
import com.mes.engineering.workinstruction.api.dto.WorkInstructionSummaryDto;
import com.mes.engineering.workinstruction.domain.RevisionStatus;
import com.mes.engineering.workinstruction.domain.WorkInstruction;
import com.mes.engineering.workinstruction.domain.WorkInstructionRevision;
import com.mes.engineering.workinstruction.kafka.WorkInstructionEventPublisher;
import com.mes.engineering.workinstruction.repository.ElectronicSignatureRepository;
import com.mes.engineering.workinstruction.repository.WorkInstructionRepository;
import com.mes.engineering.workinstruction.repository.WorkInstructionRevisionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class WorkInstructionService {

    private static final String WI_NOT_FOUND = "Work instruction not found: ";

    private final WorkInstructionRepository wiRepository;
    private final WorkInstructionRevisionRepository revisionRepository;
    private final ElectronicSignatureRepository signatureRepository;
    private final StepService stepService;
    private final SkillRequirementService skillRequirementService;
    private final SignatureService signatureService;
    private final WorkInstructionEventPublisher eventPublisher;

    public WorkInstructionService(WorkInstructionRepository wiRepository,
                                  WorkInstructionRevisionRepository revisionRepository,
                                  ElectronicSignatureRepository signatureRepository,
                                  StepService stepService,
                                  SkillRequirementService skillRequirementService,
                                  SignatureService signatureService,
                                  WorkInstructionEventPublisher eventPublisher) {
        this.wiRepository = wiRepository;
        this.revisionRepository = revisionRepository;
        this.signatureRepository = signatureRepository;
        this.stepService = stepService;
        this.skillRequirementService = skillRequirementService;
        this.signatureService = signatureService;
        this.eventPublisher = eventPublisher;
    }

    // ── Create ────────────────────────────────────────────────────────────────

    public WorkInstructionDto create(UUID orgId, CreateWorkInstructionRequest req, String actor) {
        if (wiRepository.existsByOrgIdAndIdentifier(orgId, req.getIdentifier())) {
            throw new WorkInstructionConflictException(
                    "A work instruction already exists with identifier " + req.getIdentifier());
        }
        WorkInstruction wi = new WorkInstruction();
        wi.setOrgId(orgId);
        wi.setIdentifier(req.getIdentifier());
        WorkInstruction savedWi = wiRepository.save(wi);

        WorkInstructionRevision rev = new WorkInstructionRevision();
        rev.setWorkInstruction(savedWi);
        rev.setRevision(0);
        rev.setRevisionStatus(RevisionStatus.DRAFT);
        rev.setTitle(req.getTitle());
        rev.setDescription(req.getDescription());
        rev.setPartContext(req.getPartContext());
        rev.setCustomFields(req.getCustomFields());
        WorkInstructionRevision savedRev = revisionRepository.save(rev);

        return WorkInstructionMapper.toDto(savedWi, savedRev, true, false, List.of(), List.of());
    }

    // ── Read ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public WorkInstructionDto get(UUID orgId, UUID wiId, Integer revisionNumber,
                                  RevisionStatus revisionStatus) {
        WorkInstruction wi = requireWi(orgId, wiId);
        List<WorkInstructionRevision> all = revisionRepository.findByWorkInstructionId(wi.getId());

        WorkInstructionRevision display;
        if (revisionNumber != null) {
            display = all.stream().filter(r -> r.getRevision().equals(revisionNumber)).findFirst()
                    .orElseThrow(() -> new WorkInstructionNotFoundException(
                            "No revision " + revisionNumber + " for work instruction: " + wiId));
        } else if (revisionStatus != null) {
            display = all.stream().filter(r -> r.getRevisionStatus() == revisionStatus)
                    .max(Comparator.comparingInt(WorkInstructionRevision::getRevision))
                    .orElseThrow(() -> new WorkInstructionNotFoundException(
                            "No revision with status " + revisionStatus + " for: " + wiId));
        } else {
            display = resolveDisplayRevision(all)
                    .orElseThrow(() -> new WorkInstructionNotFoundException(WI_NOT_FOUND + wiId));
        }
        return toDetailDto(wi, display, all);
    }

    @Transactional(readOnly = true)
    public Page<WorkInstructionSummaryDto> list(UUID orgId, String search, Pageable pageable) {
        Page<WorkInstruction> page = (search == null || search.isBlank())
                ? wiRepository.findByOrgIdAndDeletedFalse(orgId, pageable)
                : wiRepository.search(orgId, search.trim(), pageable);
        return page.map(wi -> {
            List<WorkInstructionRevision> all = revisionRepository.findByWorkInstructionId(wi.getId());
            WorkInstructionRevision display = resolveDisplayRevision(all)
                    .orElseThrow(() -> new WorkInstructionNotFoundException(WI_NOT_FOUND + wi.getId()));
            return WorkInstructionMapper.toSummaryDto(wi, display, hasDraft(all));
        });
    }

    @Transactional(readOnly = true)
    public List<WorkInstructionRevisionSummaryDto> listRevisions(UUID orgId, UUID wiId) {
        requireWi(orgId, wiId);
        return revisionRepository.findByWorkInstructionIdOrderByRevisionAsc(wiId).stream()
                .map(WorkInstructionMapper::toRevisionSummaryDto)
                .toList();
    }

    /** Suggest the next free identifier of the form {prefix}-NNN. */
    @Transactional(readOnly = true)
    public String suggestIdentifier(UUID orgId, String prefix) {
        String base = (prefix == null || prefix.isBlank()) ? "WI" : prefix.trim();
        int max = wiRepository.findByOrgIdAndIdentifierStartingWith(orgId, base + "-").stream()
                .map(wi -> wi.getIdentifier().substring(base.length() + 1))
                .filter(s -> s.matches("\\d+"))
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0);
        return String.format("%s-%03d", base, max + 1);
    }

    // ── Header edit / revisioning ─────────────────────────────────────────────

    public WorkInstructionDto patchHeader(UUID orgId, UUID wiId, PatchWorkInstructionHeaderRequest req) {
        WorkInstruction wi = requireWi(orgId, wiId);
        WorkInstructionRevision draft = resolveOrCreateDraft(wi);
        if (req.getTitle() != null) {
            draft.setTitle(req.getTitle());
        }
        if (req.getDescription() != null) {
            draft.setDescription(req.getDescription());
        }
        if (req.getPartContext() != null) {
            draft.setPartContext(req.getPartContext());
        }
        if (req.getReasonForRevision() != null) {
            draft.setReasonForRevision(req.getReasonForRevision());
        }
        if (req.getCustomFields() != null) {
            draft.setCustomFields(req.getCustomFields());
        }
        WorkInstructionRevision saved = revisionRepository.save(draft);
        return toDetailDto(wi, saved, revisionRepository.findByWorkInstructionId(wi.getId()));
    }

    public WorkInstructionDto createRevision(UUID orgId, UUID wiId) {
        WorkInstruction wi = requireWi(orgId, wiId);
        WorkInstructionRevision draft = resolveOrCreateDraft(wi);
        return toDetailDto(wi, draft, revisionRepository.findByWorkInstructionId(wi.getId()));
    }

    public void cancelDraft(UUID orgId, UUID wiId) {
        WorkInstruction wi = requireWi(orgId, wiId);
        WorkInstructionRevision draft = requireRevision(wi.getId(), RevisionStatus.DRAFT);
        revisionRepository.delete(draft);
        // A rev-0-only, never-approved instruction is removed entirely when its only draft is cancelled.
        List<WorkInstructionRevision> remaining = revisionRepository.findByWorkInstructionId(wi.getId());
        if (remaining.isEmpty()) {
            wiRepository.delete(wi);
        }
    }

    /** Soft delete — only permitted while the instruction has never had an APPROVED revision (FR-019). */
    public void delete(UUID orgId, UUID wiId) {
        WorkInstruction wi = requireWi(orgId, wiId);
        boolean everApproved = revisionRepository.findByWorkInstructionId(wi.getId()).stream()
                .anyMatch(r -> r.getRevisionStatus() == RevisionStatus.APPROVED);
        if (everApproved) {
            throw new WorkInstructionConflictException(
                    "Cannot delete a work instruction that has an approved revision");
        }
        wi.setDeleted(true);
        wiRepository.save(wi);
    }

    // ── Workflow (US2) ─────────────────────────────────────────────────────────

    public WorkInstructionDto submit(UUID orgId, UUID wiId, String actor) {
        WorkInstruction wi = requireWi(orgId, wiId);
        WorkInstructionRevision draft = requireRevision(wi.getId(), RevisionStatus.DRAFT);
        if (stepService.countSteps(draft.getId()) == 0) {
            throw new WorkInstructionValidationException(
                    "Cannot submit a work instruction revision with no steps");
        }
        draft.setRevisionStatus(RevisionStatus.PENDING_APPROVAL);
        draft.setSubmittedBy(actor);
        draft.setSubmittedAt(Instant.now());
        WorkInstructionRevision saved = revisionRepository.save(draft);
        return toDetailDto(wi, saved, revisionRepository.findByWorkInstructionId(wi.getId()));
    }

    /**
     * Approve a pending revision. The approver's password is verified against Keycloak and an
     * immutable electronic signature is persisted in the same transaction as the status change.
     */
    public WorkInstructionDto approve(UUID orgId, UUID wiId, Jwt jwt, String password) {
        WorkInstruction wi = requireWi(orgId, wiId);
        WorkInstructionRevision pending = requireRevision(wi.getId(), RevisionStatus.PENDING_APPROVAL);

        signatureService.verifyAndSign(jwt, password, pending, "APPROVED");

        pending.setRevisionStatus(RevisionStatus.APPROVED);
        pending.setApprovedBy(signatureService.signerOf(jwt));
        pending.setApprovedAt(Instant.now());
        WorkInstructionRevision saved = revisionRepository.save(pending);

        eventPublisher.publishApproved(wi, saved);
        return toDetailDto(wi, saved, revisionRepository.findByWorkInstructionId(wi.getId()));
    }

    public WorkInstructionDto reject(UUID orgId, UUID wiId, String actor, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new WorkInstructionValidationException("reason is required to reject a revision");
        }
        WorkInstruction wi = requireWi(orgId, wiId);
        WorkInstructionRevision pending = requireRevision(wi.getId(), RevisionStatus.PENDING_APPROVAL);
        pending.setRevisionStatus(RevisionStatus.DRAFT);
        pending.setRejectedBy(actor);
        pending.setRejectedAt(Instant.now());
        pending.setRejectionReason(reason);
        pending.setSubmittedBy(null);
        pending.setSubmittedAt(null);
        WorkInstructionRevision saved = revisionRepository.save(pending);
        return toDetailDto(wi, saved, revisionRepository.findByWorkInstructionId(wi.getId()));
    }

    // ── Editable-draft resolution (shared with step/media/skill ops) ────────────

    /** Returns the DRAFT revision for editing, auto-creating draft N+1 (copying content) if APPROVED. */
    public WorkInstructionRevision resolveOrCreateDraft(WorkInstruction wi) {
        List<WorkInstructionRevision> all = revisionRepository.findByWorkInstructionId(wi.getId());
        if (hasPending(all)) {
            throw new WorkInstructionConflictException("Cannot edit while a revision is pending approval");
        }
        Optional<WorkInstructionRevision> existingDraft = all.stream()
                .filter(r -> r.getRevisionStatus() == RevisionStatus.DRAFT)
                .findFirst();
        if (existingDraft.isPresent()) {
            return existingDraft.get();
        }
        int maxRevision = all.stream().mapToInt(WorkInstructionRevision::getRevision).max().orElse(-1);
        WorkInstructionRevision lastApproved = all.stream()
                .filter(r -> r.getRevisionStatus() == RevisionStatus.APPROVED)
                .max(Comparator.comparingInt(WorkInstructionRevision::getRevision))
                .orElseThrow(() -> new WorkInstructionConflictException(
                        "Cannot edit: no approved revision exists to base a draft on"));
        WorkInstructionRevision copy = copyRevision(lastApproved, maxRevision + 1, wi);
        WorkInstructionRevision savedCopy = revisionRepository.save(copy);
        stepService.copySteps(lastApproved.getId(), savedCopy);
        skillRequirementService.copySkillRequirements(lastApproved.getId(), savedCopy);
        return savedCopy;
    }

    /** Resolve the editable DRAFT for a work instruction (used by step/media/skill controllers). */
    public WorkInstructionRevision requireEditableDraft(UUID orgId, UUID wiId) {
        WorkInstruction wi = requireWi(orgId, wiId);
        return resolveOrCreateDraft(wi);
    }

    /**
     * Resolve a revision for read-only access: the given revision number, or the display revision
     * (APPROVED &gt; PENDING &gt; DRAFT, highest revision) when no number is supplied.
     */
    @Transactional(readOnly = true)
    public WorkInstructionRevision resolveReadRevision(UUID orgId, UUID wiId, Integer revisionNumber) {
        WorkInstruction wi = requireWi(orgId, wiId);
        List<WorkInstructionRevision> all = revisionRepository.findByWorkInstructionId(wi.getId());
        if (revisionNumber != null) {
            return all.stream().filter(r -> r.getRevision().equals(revisionNumber)).findFirst()
                    .orElseThrow(() -> new WorkInstructionNotFoundException(
                            "No revision " + revisionNumber + " for work instruction: " + wiId));
        }
        return resolveDisplayRevision(all)
                .orElseThrow(() -> new WorkInstructionNotFoundException(WI_NOT_FOUND + wiId));
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    private WorkInstructionDto toDetailDto(WorkInstruction wi, WorkInstructionRevision display,
                                           List<WorkInstructionRevision> all) {
        List<StepDto> steps = stepService.stepsOf(display.getId());
        List<SignatureDto> signatures = signatureRepository
                .findByRevisionIdOrderBySignedAtAsc(display.getId()).stream()
                .map(WorkInstructionMapper::toSignatureDto)
                .toList();
        return WorkInstructionMapper.toDto(wi, display, hasDraft(all), hasPending(all), steps, signatures);
    }

    private WorkInstructionRevision copyRevision(WorkInstructionRevision source, int newRevision,
                                                 WorkInstruction wi) {
        WorkInstructionRevision copy = new WorkInstructionRevision();
        copy.setWorkInstruction(wi);
        copy.setRevision(newRevision);
        copy.setRevisionStatus(RevisionStatus.DRAFT);
        copy.setTitle(source.getTitle());
        copy.setDescription(source.getDescription());
        copy.setPartContext(source.getPartContext());
        copy.setReasonForRevision(source.getReasonForRevision());
        copy.setCustomFields(source.getCustomFields());
        return copy;
    }

    private Optional<WorkInstructionRevision> resolveDisplayRevision(List<WorkInstructionRevision> revisions) {
        for (RevisionStatus status : new RevisionStatus[]{
                RevisionStatus.APPROVED, RevisionStatus.PENDING_APPROVAL, RevisionStatus.DRAFT}) {
            Optional<WorkInstructionRevision> match = revisions.stream()
                    .filter(r -> r.getRevisionStatus() == status)
                    .max(Comparator.comparingInt(WorkInstructionRevision::getRevision));
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    private static boolean hasDraft(List<WorkInstructionRevision> all) {
        return all.stream().anyMatch(r -> r.getRevisionStatus() == RevisionStatus.DRAFT);
    }

    private static boolean hasPending(List<WorkInstructionRevision> all) {
        return all.stream().anyMatch(r -> r.getRevisionStatus() == RevisionStatus.PENDING_APPROVAL);
    }

    private WorkInstruction requireWi(UUID orgId, UUID wiId) {
        return wiRepository.findByOrgIdAndId(orgId, wiId)
                .filter(wi -> !wi.isDeleted())
                .orElseThrow(() -> new WorkInstructionNotFoundException(WI_NOT_FOUND + wiId));
    }

    private WorkInstructionRevision requireRevision(UUID wiId, RevisionStatus status) {
        return revisionRepository.findByWorkInstructionIdAndRevisionStatus(wiId, status)
                .orElseThrow(() -> new WorkInstructionConflictException(
                        "Work instruction is not in " + status + " status: " + wiId));
    }
}
