package com.mes.inventory.itemmaster.service;

import com.mes.udf.domain.ModuleKey;
import com.mes.udf.service.UdfValidator;
import com.mes.udf.service.UdfViolation;
import com.mes.inventory.itemmaster.api.dto.CreateItemMasterRequest;
import com.mes.inventory.itemmaster.api.dto.ItemMasterDto;
import com.mes.inventory.itemmaster.api.dto.ItemRevisionMapper;
import com.mes.inventory.itemmaster.api.dto.ItemRevisionSummaryDto;
import com.mes.inventory.itemmaster.api.dto.PatchItemMasterRequest;
import com.mes.inventory.itemmaster.domain.Item;
import com.mes.inventory.itemmaster.domain.ItemRevision;
import com.mes.inventory.itemmaster.domain.RevisionStatus;
import com.mes.inventory.itemmaster.repository.ItemRepository;
import com.mes.inventory.itemmaster.repository.ItemRevisionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ItemRevisionService {

    private final ItemRepository itemRepository;
    private final ItemRevisionRepository revisionRepository;
    private final UdfValidator udfValidator;

    public ItemRevisionService(ItemRepository itemRepository,
                               ItemRevisionRepository revisionRepository,
                               UdfValidator udfValidator) {
        this.itemRepository = itemRepository;
        this.revisionRepository = revisionRepository;
        this.udfValidator = udfValidator;
    }

    /** T017 — Create item identity + initial DRAFT revision at revision=0. */
    public ItemMasterDto createItem(UUID orgId, CreateItemMasterRequest req) {
        if (itemRepository.existsByOrgIdAndPartNumber(orgId, req.getPartNumber())) {
            throw new ItemMasterConflictException(
                    "Item already exists: " + req.getPartNumber());
        }
        validateShelfLife(req.isShelfLifeControlled(), req.getShelfLifeDays());
        if (req.getCustomFields() != null) {
            validateUdf(orgId, req.getCustomFields());
        }

        Item item = new Item();
        item.setOrgId(orgId);
        item.setPartNumber(req.getPartNumber());
        Item savedItem = itemRepository.save(item);

        ItemRevision revision = ItemRevisionMapper.fromCreateRequest(req);
        revision.setItem(savedItem);
        ItemRevision saved = revisionRepository.save(revision);
        return ItemRevisionMapper.toDto(saved, true);
    }

    /** T018 — Get the display revision for an item identity. */
    @Transactional(readOnly = true)
    public ItemMasterDto getItem(UUID orgId, UUID itemId, RevisionStatus requestedStatus) {
        Item item = itemRepository.findByOrgIdAndId(orgId, itemId)
                .orElseThrow(() -> new ItemMasterNotFoundException("Item not found: " + itemId));

        List<ItemRevision> all = revisionRepository.findAllByItemId(item.getId());
        if (all.isEmpty()) {
            throw new ItemMasterNotFoundException("Item has no revisions: " + itemId);
        }

        ItemRevision display = resolveDisplayRevision(all, requestedStatus);
        if (display == null) {
            throw new ItemMasterNotFoundException(
                    "No revision with status " + requestedStatus + " for item: " + itemId);
        }
        boolean hasDraft = all.stream()
                .anyMatch(r -> r.getRevisionStatus() == RevisionStatus.DRAFT);
        return ItemRevisionMapper.toDto(display, hasDraft);
    }

    /** T019 — Transition DRAFT → PENDING_APPROVAL. */
    public ItemMasterDto submitDraft(UUID orgId, UUID itemId, String actor) {
        ItemRevision draft = getRequiredRevision(orgId, itemId, RevisionStatus.DRAFT);
        draft.setRevisionStatus(RevisionStatus.PENDING_APPROVAL);
        draft.setSubmittedBy(actor);
        draft.setSubmittedAt(Instant.now());
        ItemRevision saved = revisionRepository.save(draft);
        return ItemRevisionMapper.toDto(saved, true);
    }

    /** T019a — Transition PENDING_APPROVAL → DRAFT with rejection reason. */
    public ItemMasterDto rejectDraft(UUID orgId, UUID itemId, String actor, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ItemMasterValidationException("rejectionReason is required");
        }
        ItemRevision pending = getRequiredRevision(orgId, itemId, RevisionStatus.PENDING_APPROVAL);
        pending.setRevisionStatus(RevisionStatus.DRAFT);
        pending.setRejectedBy(actor);
        pending.setRejectedAt(Instant.now());
        pending.setRejectionReason(reason);
        pending.setSubmittedBy(null);
        pending.setSubmittedAt(null);
        ItemRevision saved = revisionRepository.save(pending);
        return ItemRevisionMapper.toDto(saved, true);
    }

    /** T020 — Transition PENDING_APPROVAL → APPROVED. */
    public ItemMasterDto approveDraft(UUID orgId, UUID itemId, String actor) {
        ItemRevision pending = getRequiredRevision(orgId, itemId, RevisionStatus.PENDING_APPROVAL);
        pending.setRevisionStatus(RevisionStatus.APPROVED);
        pending.setApprovedBy(actor);
        pending.setApprovedAt(Instant.now());
        ItemRevision saved = revisionRepository.save(pending);
        return ItemRevisionMapper.toDto(saved, false);
    }

    /**
     * T021 — Patch item (creates new DRAFT at maxApproved+1 if currently APPROVED and no DRAFT exists).
     * If a DRAFT already exists, patches it in place.
     * HTTP 409 if the item is in PENDING_APPROVAL.
     */
    public ItemMasterDto patchItem(UUID orgId, UUID itemId, PatchItemMasterRequest req, String actor) {
        Item item = itemRepository.findByOrgIdAndId(orgId, itemId)
                .orElseThrow(() -> new ItemMasterNotFoundException("Item not found: " + itemId));

        if (req.getCustomFields() != null) {
            validateUdf(orgId, req.getCustomFields());
        }

        List<ItemRevision> all = revisionRepository.findAllByItemId(item.getId());

        boolean hasPending = all.stream()
                .anyMatch(r -> r.getRevisionStatus() == RevisionStatus.PENDING_APPROVAL);
        if (hasPending) {
            throw new ItemMasterConflictException(
                    "Cannot edit item while a revision is pending approval");
        }

        ItemRevision draft = all.stream()
                .filter(r -> r.getRevisionStatus() == RevisionStatus.DRAFT)
                .findFirst()
                .orElse(null);

        if (draft == null) {
            // Auto-create DRAFT at maxApproved+1
            int maxRevision = all.stream()
                    .mapToInt(ItemRevision::getRevision)
                    .max()
                    .orElse(-1);
            ItemRevision lastApproved = all.stream()
                    .filter(r -> r.getRevisionStatus() == RevisionStatus.APPROVED)
                    .max(java.util.Comparator.comparingInt(ItemRevision::getRevision))
                    .orElseThrow(() -> new ItemMasterConflictException(
                            "Cannot edit: no approved revision exists to base the draft on"));
            draft = copyRevision(lastApproved, maxRevision + 1, item);
            draft = revisionRepository.save(draft);
        }

        validateShelfLifeForPatch(req, draft);
        ItemRevisionMapper.applyPatch(req, draft);
        ItemRevision saved = revisionRepository.save(draft);

        boolean hasDraft = true;
        return ItemRevisionMapper.toDto(saved, hasDraft);
    }

    /** T022 — Hard-delete the current DRAFT revision. */
    public void cancelDraft(UUID orgId, UUID itemId) {
        ItemRevision draft = getRequiredRevision(orgId, itemId, RevisionStatus.DRAFT);
        revisionRepository.delete(draft);
    }

    /** T023 — List display revisions for all items, one per identity. Supports optional filters. */
    @Transactional(readOnly = true)
    public Page<ItemMasterDto> listItems(UUID orgId, RevisionStatus status,
                                          String search, String makeBuyCode,
                                          String counterfeitRiskLevel, Pageable pageable) {
        Page<Item> items = itemRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("orgId"), orgId), pageable);

        final String searchLower = (search != null && !search.isBlank())
                ? search.toLowerCase(java.util.Locale.ROOT) : null;

        List<ItemMasterDto> dtos = items.getContent().stream()
                .map(item -> {
                    List<ItemRevision> all = revisionRepository.findAllByItemId(item.getId());
                    if (all.isEmpty()) {
                        return null;
                    }
                    ItemRevision display = resolveDisplayRevision(all, status);
                    if (display == null) {
                        return null;
                    }
                    // Java-side post-filtering
                    if (searchLower != null) {
                        boolean matchPn = display.getItem().getPartNumber() != null
                                && display.getItem().getPartNumber()
                                        .toLowerCase(java.util.Locale.ROOT).contains(searchLower);
                        boolean matchDesc = display.getDescription() != null
                                && display.getDescription()
                                        .toLowerCase(java.util.Locale.ROOT).contains(searchLower);
                        if (!matchPn && !matchDesc) {
                            return null;
                        }
                    }
                    if (makeBuyCode != null && display.getMakeBuyCode() != null
                            && !display.getMakeBuyCode().name().equalsIgnoreCase(makeBuyCode)) {
                        return null;
                    }
                    if (counterfeitRiskLevel != null && display.getCounterfeitRiskLevel() != null
                            && !display.getCounterfeitRiskLevel().name()
                                    .equalsIgnoreCase(counterfeitRiskLevel)) {
                        return null;
                    }
                    boolean hasDraft = all.stream()
                            .anyMatch(r -> r.getRevisionStatus() == RevisionStatus.DRAFT);
                    return ItemRevisionMapper.toDto(display, hasDraft);
                })
                .filter(dto -> dto != null)
                .toList();

        return new org.springframework.data.domain.PageImpl<>(dtos, pageable, items.getTotalElements());
    }

    /** T036a — Clone from APPROVED source. HTTP 422 if no APPROVED exists. */
    @Transactional(readOnly = true)
    public ItemMasterDto cloneAsTemplate(UUID orgId, UUID itemId) {
        Item item = itemRepository.findByOrgIdAndId(orgId, itemId)
                .orElseThrow(() -> new ItemMasterNotFoundException("Item not found: " + itemId));

        List<ItemRevision> all = revisionRepository.findAllByItemId(item.getId());
        ItemRevision approved = all.stream()
                .filter(r -> r.getRevisionStatus() == RevisionStatus.APPROVED)
                .max(java.util.Comparator.comparingInt(ItemRevision::getRevision))
                .orElseThrow(() -> new ItemMasterValidationException(
                        "Cannot clone: item has no approved revision"));

        boolean hasDraft = all.stream()
                .anyMatch(r -> r.getRevisionStatus() == RevisionStatus.DRAFT);
        ItemMasterDto dto = ItemRevisionMapper.toDto(approved, hasDraft);
        dto.setId(null);
        dto.setRevisionId(null);
        dto.setPartNumber(null);
        dto.setRevision(null);
        return dto;
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private ItemRevision getRequiredRevision(UUID orgId, UUID itemId, RevisionStatus expectedStatus) {
        Item item = itemRepository.findByOrgIdAndId(orgId, itemId)
                .orElseThrow(() -> new ItemMasterNotFoundException("Item not found: " + itemId));

        return revisionRepository.findByItemIdAndRevisionStatus(item.getId(), expectedStatus)
                .orElseThrow(() -> new ItemMasterConflictException(
                        "No " + expectedStatus + " revision exists for item: " + itemId));
    }

    private static ItemRevision resolveDisplayRevision(List<ItemRevision> revisions,
                                                        RevisionStatus requestedStatus) {
        if (requestedStatus != null) {
            return revisions.stream()
                    .filter(r -> r.getRevisionStatus() == requestedStatus)
                    .max(java.util.Comparator.comparingInt(ItemRevision::getRevision))
                    .orElse(null);
        }
        // FR-011: APPROVED preferred, then PENDING_APPROVAL, then DRAFT
        for (RevisionStatus preferred : new RevisionStatus[]{RevisionStatus.APPROVED,
                RevisionStatus.PENDING_APPROVAL, RevisionStatus.DRAFT}) {
            var match = revisions.stream()
                    .filter(r -> r.getRevisionStatus() == preferred)
                    .max(java.util.Comparator.comparingInt(ItemRevision::getRevision));
            if (match.isPresent()) {
                return match.get();
            }
        }
        return revisions.get(0);
    }

    private ItemRevision copyRevision(ItemRevision source, int newRevision, Item item) {
        ItemRevision copy = new ItemRevision();
        copy.setItem(item);
        copy.setRevision(newRevision);
        copy.setRevisionStatus(RevisionStatus.DRAFT);
        copy.setDescription(source.getDescription());
        copy.setUnitOfMeasure(source.getUnitOfMeasure());
        copy.setCageCode(source.getCageCode());
        copy.setClassification(source.getClassification());
        copy.setMakeBuyCode(source.getMakeBuyCode());
        copy.setTraceabilityMethod(source.getTraceabilityMethod());
        copy.setShelfLifeControlled(source.isShelfLifeControlled());
        copy.setShelfLifeDays(source.getShelfLifeDays());
        copy.setStepPartRef(source.getStepPartRef());
        copy.setCounterfeitRiskLevel(source.getCounterfeitRiskLevel());
        copy.setApprovedSuppliers(source.getApprovedSuppliers());
        copy.setVerificationRequired(source.isVerificationRequired());
        copy.setCustomFields(source.getCustomFields());
        return copy;
    }

    /** T084 — Return all revisions for an item, ordered by revision ASC. */
    @Transactional(readOnly = true)
    public List<ItemRevisionSummaryDto> listRevisions(UUID orgId, UUID itemId) {
        itemRepository.findByOrgIdAndId(orgId, itemId)
                .orElseThrow(() -> new ItemMasterNotFoundException(itemId.toString()));
        return revisionRepository.findByItemIdOrderByRevisionAsc(itemId)
                .stream()
                .map(ItemRevisionMapper::toSummaryDto)
                .collect(Collectors.toList());
    }

    private void validateShelfLife(boolean controlled, Integer days) {
        if (controlled && days == null) {
            throw new ItemMasterValidationException(
                    "shelfLifeDays is required when shelfLifeControlled is true");
        }
    }

    private void validateShelfLifeForPatch(PatchItemMasterRequest patch, ItemRevision current) {
        boolean effectiveControlled = patch.getShelfLifeControlled() != null
                ? patch.getShelfLifeControlled()
                : current.isShelfLifeControlled();
        Integer effectiveDays = patch.getShelfLifeDays() != null
                ? patch.getShelfLifeDays()
                : current.getShelfLifeDays();
        validateShelfLife(effectiveControlled, effectiveDays);
    }

    private void validateUdf(UUID orgId, java.util.Map<String, Object> customFields) {
        List<UdfViolation> violations = udfValidator.validate(orgId, ModuleKey.ITEM_MASTER, customFields);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(v -> v.fieldKey() + ": " + v.message())
                    .collect(Collectors.joining("; "));
            throw new ItemMasterValidationException(message);
        }
    }
}
