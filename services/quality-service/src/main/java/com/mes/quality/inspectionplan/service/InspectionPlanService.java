package com.mes.quality.inspectionplan.service;

import com.mes.quality.inspectionplan.api.dto.CreateInspectionPlanRequest;
import com.mes.quality.inspectionplan.api.dto.InspectionPlanDto;
import com.mes.quality.inspectionplan.api.dto.InspectionPlanMapper;
import com.mes.quality.inspectionplan.api.dto.InspectionPlanRevisionSummaryDto;
import com.mes.quality.inspectionplan.api.dto.InspectionPlanSummaryDto;
import com.mes.quality.inspectionplan.api.dto.PatchInspectionPlanHeaderRequest;
import com.mes.quality.inspectionplan.client.InventoryServiceClient;
import com.mes.quality.inspectionplan.domain.InspectionPlan;
import com.mes.quality.inspectionplan.domain.InspectionPlanRevision;
import com.mes.quality.inspectionplan.domain.RevisionStatus;
import com.mes.quality.inspectionplan.repository.InspectionPlanRepository;
import com.mes.quality.inspectionplan.repository.InspectionPlanRevisionRepository;
import com.mes.quality.service.QualityConflictException;
import com.mes.quality.service.QualityNotFoundException;
import com.mes.quality.service.QualityValidationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class InspectionPlanService {

    private static final String PLAN_NOT_FOUND = "Inspection plan not found: ";

    private final InspectionPlanRepository planRepository;
    private final InspectionPlanRevisionRepository revisionRepository;
    private final InventoryServiceClient inventoryClient;
    private final CharacteristicService characteristicService;
    private final com.mes.quality.kafka.QualityEventPublisher eventPublisher;

    public InspectionPlanService(InspectionPlanRepository planRepository,
                                 InspectionPlanRevisionRepository revisionRepository,
                                 InventoryServiceClient inventoryClient,
                                 CharacteristicService characteristicService,
                                 com.mes.quality.kafka.QualityEventPublisher eventPublisher) {
        this.planRepository = planRepository;
        this.revisionRepository = revisionRepository;
        this.inventoryClient = inventoryClient;
        this.characteristicService = characteristicService;
        this.eventPublisher = eventPublisher;
    }

    public InspectionPlanDto createPlan(UUID orgId, CreateInspectionPlanRequest req) {
        InventoryServiceClient.ItemSummary item = inventoryClient.findItem(req.getItemId())
                .orElseThrow(() -> new QualityValidationException(
                        "Item not found: " + req.getItemId()));
        if (planRepository.existsByOrgIdAndItemId(orgId, req.getItemId())) {
            throw new QualityConflictException(
                    "An inspection plan already exists for item " + req.getItemId());
        }

        InspectionPlan plan = new InspectionPlan();
        plan.setOrgId(orgId);
        plan.setItemId(req.getItemId());
        plan.setPartNumber(item.partNumber());
        InspectionPlan savedPlan = planRepository.save(plan);

        InspectionPlanRevision revision = new InspectionPlanRevision();
        revision.setInspectionPlan(savedPlan);
        revision.setRevision(0);
        revision.setRevisionStatus(RevisionStatus.DRAFT);
        revision.setName(req.getName());
        revision.setDescription(req.getDescription());
        revision.setCustomFields(req.getCustomFields());
        InspectionPlanRevision savedRevision = revisionRepository.save(revision);

        return InspectionPlanMapper.toDto(savedPlan, savedRevision, true, false);
    }

    @Transactional(readOnly = true)
    public InspectionPlanDto getPlan(UUID orgId, UUID planId, Integer revisionNumber,
                                     RevisionStatus revisionStatus) {
        InspectionPlan plan = requirePlan(orgId, planId);
        List<InspectionPlanRevision> all = revisionRepository.findByInspectionPlanId(plan.getId());

        InspectionPlanRevision display;
        if (revisionNumber != null) {
            display = all.stream()
                    .filter(r -> r.getRevision().equals(revisionNumber))
                    .findFirst()
                    .orElseThrow(() -> new QualityNotFoundException(
                            "No revision " + revisionNumber + " for plan: " + planId));
        } else if (revisionStatus != null) {
            display = all.stream()
                    .filter(r -> r.getRevisionStatus() == revisionStatus)
                    .max(Comparator.comparingInt(InspectionPlanRevision::getRevision))
                    .orElseThrow(() -> new QualityNotFoundException(
                            "No revision with status " + revisionStatus + " for plan: " + planId));
        } else {
            display = resolveDisplayRevision(all)
                    .orElseThrow(() -> new QualityNotFoundException(PLAN_NOT_FOUND + planId));
        }
        return InspectionPlanMapper.toDto(plan, display, hasDraft(all), hasPending(all));
    }

    public InspectionPlanDto submit(UUID orgId, UUID planId, String actor) {
        InspectionPlanRevision draft = requireRevision(orgId, planId, RevisionStatus.DRAFT);
        // Submit-gate: a plan must have at least one characteristic and every CALCULATED
        // expression must still be valid (a peer may have been deleted/retyped since save).
        var characteristics = characteristicService.allInRevision(draft.getId());
        if (characteristics.isEmpty()) {
            throw new QualityValidationException(
                    "Cannot submit an inspection plan with no characteristics");
        }
        characteristicService.revalidateExpressions(characteristics);
        draft.setRevisionStatus(RevisionStatus.PENDING_APPROVAL);
        draft.setSubmittedBy(actor);
        draft.setSubmittedAt(Instant.now());
        InspectionPlanRevision saved = revisionRepository.save(draft);
        return InspectionPlanMapper.toDto(saved.getInspectionPlan(), saved, false, true);
    }

    public InspectionPlanDto approve(UUID orgId, UUID planId, String actor) {
        InspectionPlanRevision pending = requireRevision(orgId, planId, RevisionStatus.PENDING_APPROVAL);
        pending.setRevisionStatus(RevisionStatus.APPROVED);
        pending.setApprovedBy(actor);
        pending.setApprovedAt(Instant.now());
        InspectionPlanRevision saved = revisionRepository.save(pending);
        InspectionPlan plan = saved.getInspectionPlan();
        boolean hasDraft = revisionRepository
                .existsByInspectionPlanIdAndRevisionStatus(plan.getId(), RevisionStatus.DRAFT);
        eventPublisher.publishApproved(plan, saved);
        return InspectionPlanMapper.toDto(plan, saved, hasDraft, false);
    }

    public InspectionPlanDto reject(UUID orgId, UUID planId, String actor, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new QualityValidationException("reason is required to reject a revision");
        }
        InspectionPlanRevision pending = requireRevision(orgId, planId, RevisionStatus.PENDING_APPROVAL);
        pending.setRevisionStatus(RevisionStatus.DRAFT);
        pending.setRejectedBy(actor);
        pending.setRejectedAt(Instant.now());
        pending.setRejectionReason(reason);
        pending.setSubmittedBy(null);
        pending.setSubmittedAt(null);
        InspectionPlanRevision saved = revisionRepository.save(pending);
        return InspectionPlanMapper.toDto(saved.getInspectionPlan(), saved, true, false);
    }

    public void cancelDraft(UUID orgId, UUID planId) {
        InspectionPlanRevision draft = requireRevision(orgId, planId, RevisionStatus.DRAFT);
        revisionRepository.delete(draft);
    }

    public InspectionPlanDto patchHeader(UUID orgId, UUID planId, PatchInspectionPlanHeaderRequest req) {
        InspectionPlan plan = requirePlan(orgId, planId);
        InspectionPlanRevision draft = resolveOrCreateDraft(plan);

        if (req.getName() != null) {
            draft.setName(req.getName());
        }
        if (req.getDescription() != null) {
            draft.setDescription(req.getDescription());
        }
        if (req.getReasonForRevision() != null) {
            draft.setReasonForRevision(req.getReasonForRevision());
        }
        if (req.getCustomFields() != null) {
            draft.setCustomFields(req.getCustomFields());
        }
        InspectionPlanRevision saved = revisionRepository.save(draft);
        return InspectionPlanMapper.toDto(plan, saved, true, false);
    }

    public InspectionPlanDto createRevision(UUID orgId, UUID planId) {
        InspectionPlan plan = requirePlan(orgId, planId);
        InspectionPlanRevision draft = resolveOrCreateDraft(plan);
        return InspectionPlanMapper.toDto(plan, draft, true, false);
    }

    public void deletePlan(UUID orgId, UUID planId) {
        InspectionPlan plan = requirePlan(orgId, planId);
        List<InspectionPlanRevision> all = revisionRepository.findByInspectionPlanId(plan.getId());
        boolean everApproved = all.stream()
                .anyMatch(r -> r.getRevisionStatus() == RevisionStatus.APPROVED);
        if (everApproved) {
            throw new QualityConflictException(
                    "Cannot delete an inspection plan that has an approved revision");
        }
        revisionRepository.deleteAll(all);
        planRepository.delete(plan);
    }

    @Transactional(readOnly = true)
    public Page<InspectionPlanSummaryDto> listPlans(UUID orgId, String search, Pageable pageable) {
        Page<InspectionPlan> page = (search == null || search.isBlank())
                ? planRepository.findByOrgId(orgId, pageable)
                : planRepository.search(orgId, search.trim(), pageable);
        return page.map(plan -> {
            List<InspectionPlanRevision> all = revisionRepository.findByInspectionPlanId(plan.getId());
            InspectionPlanRevision display = resolveDisplayRevision(all)
                    .orElseThrow(() -> new QualityNotFoundException(PLAN_NOT_FOUND + plan.getId()));
            return InspectionPlanMapper.toSummaryDto(plan, display, hasDraft(all));
        });
    }

    @Transactional(readOnly = true)
    public com.mes.quality.inspectionplan.api.dto.ApprovedPlanDto getApprovedByItem(UUID orgId, UUID itemId) {
        InspectionPlan plan = planRepository.findByOrgIdAndItemId(orgId, itemId)
                .orElseThrow(() -> new QualityNotFoundException(
                        "NO_APPROVED_PLAN: no inspection plan for item " + itemId));
        InspectionPlanRevision approved = latestApproved(plan)
                .orElseThrow(() -> new QualityNotFoundException(
                        "NO_APPROVED_PLAN: no approved revision for item " + itemId));
        var characteristics = characteristicService.list(orgId, plan.getId(), approved.getRevision());
        return new com.mes.quality.inspectionplan.api.dto.ApprovedPlanDto(
                plan.getId(), plan.getItemId(), plan.getPartNumber(),
                approved.getRevision(), approved.getName(), characteristics);
    }

    @Transactional(readOnly = true)
    public com.mes.quality.inspectionplan.api.dto.PlanStatusDto statusByItem(UUID orgId, UUID itemId) {
        InspectionPlan plan = planRepository.findByOrgIdAndItemId(orgId, itemId).orElse(null);
        if (plan == null) {
            return new com.mes.quality.inspectionplan.api.dto.PlanStatusDto(false, false, null);
        }
        return latestApproved(plan)
                .map(r -> new com.mes.quality.inspectionplan.api.dto.PlanStatusDto(true, true, r.getRevision()))
                .orElseGet(() -> new com.mes.quality.inspectionplan.api.dto.PlanStatusDto(true, false, null));
    }

    private java.util.Optional<InspectionPlanRevision> latestApproved(InspectionPlan plan) {
        return revisionRepository.findByInspectionPlanId(plan.getId()).stream()
                .filter(r -> r.getRevisionStatus() == RevisionStatus.APPROVED)
                .max(Comparator.comparingInt(InspectionPlanRevision::getRevision));
    }

    @Transactional(readOnly = true)
    public List<InspectionPlanRevisionSummaryDto> listRevisions(UUID orgId, UUID planId) {
        requirePlan(orgId, planId);
        return revisionRepository.findByInspectionPlanIdOrderByRevisionAsc(planId)
                .stream()
                .map(InspectionPlanMapper::toRevisionSummaryDto)
                .toList();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Returns the open draft, or copies the latest approved into a new draft revision N+1. */
    private InspectionPlanRevision resolveOrCreateDraft(InspectionPlan plan) {
        List<InspectionPlanRevision> all = revisionRepository.findByInspectionPlanId(plan.getId());
        if (hasPending(all)) {
            throw new QualityConflictException(
                    "Cannot edit while a revision is pending approval");
        }
        InspectionPlanRevision draft = all.stream()
                .filter(r -> r.getRevisionStatus() == RevisionStatus.DRAFT)
                .findFirst()
                .orElse(null);
        if (draft != null) {
            return draft;
        }
        int maxRevision = all.stream().mapToInt(InspectionPlanRevision::getRevision).max().orElse(-1);
        InspectionPlanRevision lastApproved = all.stream()
                .filter(r -> r.getRevisionStatus() == RevisionStatus.APPROVED)
                .max(Comparator.comparingInt(InspectionPlanRevision::getRevision))
                .orElseThrow(() -> new QualityConflictException(
                        "Cannot edit: no approved revision exists to base a draft on"));
        InspectionPlanRevision copy = copyRevision(lastApproved, maxRevision + 1, plan);
        InspectionPlanRevision savedCopy = revisionRepository.save(copy);
        characteristicService.copyCharacteristics(lastApproved.getId(), savedCopy);
        return savedCopy;
    }

    private InspectionPlanRevision copyRevision(InspectionPlanRevision source, int newRevision,
                                                InspectionPlan plan) {
        InspectionPlanRevision copy = new InspectionPlanRevision();
        copy.setInspectionPlan(plan);
        copy.setRevision(newRevision);
        copy.setRevisionStatus(RevisionStatus.DRAFT);
        copy.setName(source.getName());
        copy.setDescription(source.getDescription());
        copy.setReasonForRevision(source.getReasonForRevision());
        copy.setCustomFields(source.getCustomFields());
        // Characteristic copy-on-revision is wired in US2 (CharacteristicService).
        return copy;
    }

    private java.util.Optional<InspectionPlanRevision> resolveDisplayRevision(
            List<InspectionPlanRevision> revisions) {
        for (RevisionStatus status : new RevisionStatus[]{
                RevisionStatus.APPROVED, RevisionStatus.PENDING_APPROVAL, RevisionStatus.DRAFT}) {
            java.util.Optional<InspectionPlanRevision> match = revisions.stream()
                    .filter(r -> r.getRevisionStatus() == status)
                    .max(Comparator.comparingInt(InspectionPlanRevision::getRevision));
            if (match.isPresent()) {
                return match;
            }
        }
        return java.util.Optional.empty();
    }

    private static boolean hasDraft(List<InspectionPlanRevision> all) {
        return all.stream().anyMatch(r -> r.getRevisionStatus() == RevisionStatus.DRAFT);
    }

    private static boolean hasPending(List<InspectionPlanRevision> all) {
        return all.stream().anyMatch(r -> r.getRevisionStatus() == RevisionStatus.PENDING_APPROVAL);
    }

    private InspectionPlan requirePlan(UUID orgId, UUID planId) {
        return planRepository.findByOrgIdAndId(orgId, planId)
                .orElseThrow(() -> new QualityNotFoundException(PLAN_NOT_FOUND + planId));
    }

    private InspectionPlanRevision requireRevision(UUID orgId, UUID planId, RevisionStatus status) {
        InspectionPlan plan = requirePlan(orgId, planId);
        return revisionRepository.findByInspectionPlanIdAndRevisionStatus(plan.getId(), status)
                .orElseThrow(() -> new QualityConflictException(
                        "Plan is not in " + status + " status: " + planId));
    }
}
