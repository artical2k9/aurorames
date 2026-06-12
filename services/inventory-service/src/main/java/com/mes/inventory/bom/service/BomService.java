package com.mes.inventory.bom.service;

import com.mes.inventory.bom.api.dto.BomDto;
import com.mes.inventory.bom.api.dto.BomLineDto;
import com.mes.inventory.bom.api.dto.BomMapper;
import com.mes.inventory.bom.api.dto.BomRevisionSummaryDto;
import com.mes.inventory.bom.api.dto.BomSummaryDto;
import com.mes.inventory.bom.api.dto.CreateBomLineRequest;
import com.mes.inventory.bom.api.dto.CreateBomRequest;
import com.mes.inventory.bom.api.dto.PatchBomHeaderRequest;
import com.mes.inventory.bom.api.dto.UpdateBomLineRequest;
import com.mes.inventory.bom.domain.Bom;
import com.mes.inventory.bom.domain.BomLine;
import com.mes.inventory.bom.domain.BomRevision;
import com.mes.inventory.bom.domain.EffectivityMethod;
import com.mes.inventory.bom.repository.BomLineRepository;
import com.mes.inventory.bom.repository.BomRepository;
import com.mes.inventory.bom.repository.BomRevisionRepository;
import com.mes.inventory.itemmaster.domain.CounterfeitRiskLevel;
import com.mes.inventory.itemmaster.domain.ItemRevision;
import com.mes.inventory.itemmaster.domain.RevisionStatus;
import com.mes.inventory.itemmaster.repository.ItemRepository;
import com.mes.inventory.itemmaster.repository.ItemRevisionRepository;
import com.mes.inventory.kafka.BomEventPublisher;
import com.mes.inventory.kafka.ItemMasterEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class BomService {

    private static final String BOM_NOT_FOUND = "BOM not found: ";

    private final BomRepository bomRepository;
    private final BomRevisionRepository bomRevisionRepository;
    private final BomLineRepository bomLineRepository;
    private final ItemRepository itemRepository;
    private final ItemRevisionRepository itemRevisionRepository;
    private final BomEventPublisher bomEventPublisher;
    private final ItemMasterEventPublisher itemMasterEventPublisher;
    private final EffectivityValidator effectivityValidator;

    public BomService(BomRepository bomRepository,
                      BomRevisionRepository bomRevisionRepository,
                      BomLineRepository bomLineRepository,
                      ItemRepository itemRepository,
                      ItemRevisionRepository itemRevisionRepository,
                      BomEventPublisher bomEventPublisher,
                      ItemMasterEventPublisher itemMasterEventPublisher,
                      EffectivityValidator effectivityValidator) {
        this.bomRepository = bomRepository;
        this.bomRevisionRepository = bomRevisionRepository;
        this.bomLineRepository = bomLineRepository;
        this.itemRepository = itemRepository;
        this.itemRevisionRepository = itemRevisionRepository;
        this.bomEventPublisher = bomEventPublisher;
        this.itemMasterEventPublisher = itemMasterEventPublisher;
        this.effectivityValidator = effectivityValidator;
    }

    public BomDto createBom(UUID orgId, CreateBomRequest req) {
        if (itemRepository.findByOrgIdAndId(orgId, req.getParentItemId()).isEmpty()) {
            throw new BomValidationException("Parent item not found: " + req.getParentItemId());
        }
        if (bomRepository.existsByOrgIdAndParentItemId(orgId, req.getParentItemId())) {
            throw new BomConflictException("BOM already exists for item " + req.getParentItemId());
        }

        Bom bom = new Bom();
        bom.setOrgId(orgId);
        bom.setParentItemId(req.getParentItemId());
        Bom savedBom = bomRepository.save(bom);

        BomRevision revision = new BomRevision();
        revision.setBom(savedBom);
        revision.setRevision(0);
        revision.setDescription(req.getDescription());
        revision.setEcoId(req.getEcoId());
        BomRevision savedRevision = bomRevisionRepository.save(revision);

        return BomMapper.toDto(savedBom, savedRevision, true, false);
    }

    @Transactional(readOnly = true)
    public BomDto getBom(UUID orgId, UUID bomId, RevisionStatus requestedStatus, Integer revisionNumber) {
        Bom bom = bomRepository.findByOrgIdAndId(orgId, bomId)
                .orElseThrow(() -> new BomNotFoundException(BOM_NOT_FOUND + bomId));
        return buildDto(bom, requestedStatus, revisionNumber);
    }

    public BomDto submitDraft(UUID orgId, UUID bomId, String actor) {
        BomRevision draft = getRequiredRevision(orgId, bomId, RevisionStatus.DRAFT);
        draft.setRevisionStatus(RevisionStatus.PENDING_APPROVAL);
        draft.setSubmittedBy(actor);
        draft.setSubmittedAt(Instant.now());
        BomRevision saved = bomRevisionRepository.save(draft);
        Bom bom = saved.getBom();
        return BomMapper.toDto(bom, saved, false, true);
    }

    public BomDto approveDraft(UUID orgId, UUID bomId, String actor) {
        BomRevision pending = getRequiredRevision(orgId, bomId, RevisionStatus.PENDING_APPROVAL);
        pending.setRevisionStatus(RevisionStatus.APPROVED);
        pending.setApprovedBy(actor);
        pending.setApprovedAt(Instant.now());
        BomRevision saved = bomRevisionRepository.save(pending);
        Bom bom = saved.getBom();
        boolean hasDraft = bomRevisionRepository
                .existsByBomIdAndRevisionStatus(bom.getId(), RevisionStatus.DRAFT);
        bomEventPublisher.publishApproved(bom, saved);
        return BomMapper.toDto(bom, saved, hasDraft, false);
    }

    public BomDto rejectDraft(UUID orgId, UUID bomId, String actor, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BomValidationException("rejectionReason is required");
        }
        BomRevision pending = getRequiredRevision(orgId, bomId, RevisionStatus.PENDING_APPROVAL);
        pending.setRevisionStatus(RevisionStatus.DRAFT);
        pending.setRejectedBy(actor);
        pending.setRejectedAt(Instant.now());
        pending.setRejectionReason(reason);
        pending.setSubmittedBy(null);
        pending.setSubmittedAt(null);
        BomRevision saved = bomRevisionRepository.save(pending);
        Bom bom = saved.getBom();
        return BomMapper.toDto(bom, saved, true, false);
    }

    public void cancelDraft(UUID orgId, UUID bomId) {
        BomRevision draft = getRequiredRevision(orgId, bomId, RevisionStatus.DRAFT);
        bomRevisionRepository.delete(draft);
    }

    public BomLineDto addLine(UUID orgId, UUID bomId, CreateBomLineRequest req) {
        Bom bom = bomRepository.findByOrgIdAndId(orgId, bomId)
                .orElseThrow(() -> new BomNotFoundException(BOM_NOT_FOUND + bomId));

        BomRevision draft = bomRevisionRepository
                .findByBomIdAndRevisionStatus(bom.getId(), RevisionStatus.DRAFT)
                .orElseThrow(() -> new BomConflictException(
                        "Cannot modify a BOM that is not in DRAFT status"));

        ItemRevision componentRevision = itemRevisionRepository
                .findById(req.getComponentItemRevisionId())
                .orElseThrow(() -> new BomValidationException(
                        "Component item revision not found: " + req.getComponentItemRevisionId()));
        if (!componentRevision.getItem().getOrgId().equals(orgId)) {
            throw new BomValidationException(
                    "Component item revision not found: " + req.getComponentItemRevisionId());
        }

        EffectivityMethod method = req.getEffectivityMethod();
        if (method == EffectivityMethod.DATE) {
            if (req.getEffectiveFromDate() == null) {
                throw new BomValidationException("effectiveFromDate is required for DATE effectivity");
            }
            effectivityValidator.validateNewLine(draft.getId(), req);
        } else if (method == EffectivityMethod.UNIT) {
            if (req.getEffectiveFromUnit() == null) {
                throw new BomValidationException("effectiveFromUnit is required for UNIT effectivity");
            }
            if (bomLineRepository.existsByBomRevisionIdAndFindNumber(draft.getId(), req.getFindNumber())) {
                throw new BomConflictException("Find number already exists: " + req.getFindNumber());
            }
        } else {
            if (bomLineRepository.existsByBomRevisionIdAndFindNumber(draft.getId(), req.getFindNumber())) {
                throw new BomConflictException("Find number already exists: " + req.getFindNumber());
            }
        }

        if (bomRepository.hasAncestorCycle(bom.getId(), req.getComponentItemRevisionId())) {
            throw new BomValidationException("Adding this component would create a circular reference");
        }

        BomLine line = new BomLine();
        line.setBomRevision(draft);
        line.setComponentItemRevision(componentRevision);
        line.setQuantity(req.getQuantity());
        line.setUnitOfMeasure(req.getUnitOfMeasure());
        line.setFindNumber(req.getFindNumber());
        line.setReferenceDesignators(req.getReferenceDesignators());
        line.setEffectivityMethod(req.getEffectivityMethod());
        line.setEffectiveFromDate(req.getEffectiveFromDate());
        line.setEffectiveToDate(req.getEffectiveToDate());
        line.setEffectiveFromUnit(req.getEffectiveFromUnit());
        line.setEffectiveToUnit(req.getEffectiveToUnit());
        BomLine saved = bomLineRepository.save(line);

        CounterfeitRiskLevel risk = componentRevision.getCounterfeitRiskLevel();
        if (risk == CounterfeitRiskLevel.HIGH || risk == CounterfeitRiskLevel.CRITICAL) {
            itemMasterEventPublisher.publishAs5553RiskAdded(componentRevision);
        }

        return BomMapper.toLineDto(saved);
    }

    @Transactional(readOnly = true)
    public List<BomLineDto> listEnrichedLines(UUID orgId, UUID bomId, Integer revisionNumber) {
        Bom bom = bomRepository.findByOrgIdAndId(orgId, bomId)
                .orElseThrow(() -> new BomNotFoundException(BOM_NOT_FOUND + bomId));
        List<BomRevision> all = bomRevisionRepository.findAllByBomId(bom.getId());
        BomRevision targetRevision;
        if (revisionNumber != null) {
            targetRevision = all.stream()
                    .filter(r -> r.getRevision().equals(revisionNumber))
                    .findFirst()
                    .orElseThrow(() -> new BomNotFoundException(
                            "No revision " + revisionNumber + " for BOM: " + bomId));
        } else {
            targetRevision = resolveDisplayRevision(all);
        }
        if (targetRevision == null) {
            return List.of();
        }
        return bomLineRepository.findAllByBomRevisionId(targetRevision.getId())
                .stream()
                .map(BomMapper::toLineDto)
                .toList();
    }


    public BomLineDto updateLine(UUID orgId, UUID bomId, UUID lineId, UpdateBomLineRequest req) {
        Bom bom = bomRepository.findByOrgIdAndId(orgId, bomId)
                .orElseThrow(() -> new BomNotFoundException(BOM_NOT_FOUND + bomId));
        BomRevision draft = bomRevisionRepository
                .findByBomIdAndRevisionStatus(bom.getId(), RevisionStatus.DRAFT)
                .orElseThrow(() -> new BomConflictException(
                        "Cannot modify a BOM that is not in DRAFT status"));
        BomLine line = bomLineRepository.findById(lineId)
                .filter(l -> l.getBomRevision().getId().equals(draft.getId()))
                .orElseThrow(() -> new BomNotFoundException("BOM line not found: " + lineId));
        applyScalarUpdates(line, draft.getId(), req);
        applyEffectivityUpdates(line, draft.getId(), lineId, req);
        return BomMapper.toLineDto(bomLineRepository.save(line));
    }

    public void deleteLine(UUID orgId, UUID bomId, UUID lineId) {
        Bom bom = bomRepository.findByOrgIdAndId(orgId, bomId)
                .orElseThrow(() -> new BomNotFoundException(BOM_NOT_FOUND + bomId));
        BomRevision draft = bomRevisionRepository
                .findByBomIdAndRevisionStatus(bom.getId(), RevisionStatus.DRAFT)
                .orElseThrow(() -> new BomConflictException(
                        "Cannot modify a BOM that is not in DRAFT status"));
        BomLine line = bomLineRepository.findById(lineId)
                .filter(l -> l.getBomRevision().getId().equals(draft.getId()))
                .orElseThrow(() -> new BomNotFoundException("BOM line not found: " + lineId));
        bomLineRepository.delete(line);
    }

    public BomDto patchHeader(UUID orgId, UUID bomId, PatchBomHeaderRequest req) {
        Bom bom = bomRepository.findByOrgIdAndId(orgId, bomId)
                .orElseThrow(() -> new BomNotFoundException(BOM_NOT_FOUND + bomId));

        List<BomRevision> all = bomRevisionRepository.findAllByBomId(bom.getId());

        boolean hasPending = all.stream()
                .anyMatch(r -> r.getRevisionStatus() == RevisionStatus.PENDING_APPROVAL);
        if (hasPending) {
            throw new BomConflictException("Cannot edit BOM while a revision is pending approval");
        }

        BomRevision draft = all.stream()
                .filter(r -> r.getRevisionStatus() == RevisionStatus.DRAFT)
                .findFirst()
                .orElse(null);

        if (draft == null) {
            int maxRevision = all.stream()
                    .mapToInt(BomRevision::getRevision)
                    .max()
                    .orElse(-1);
            BomRevision lastApproved = all.stream()
                    .filter(r -> r.getRevisionStatus() == RevisionStatus.APPROVED)
                    .max(Comparator.comparingInt(BomRevision::getRevision))
                    .orElseThrow(() -> new BomConflictException(
                            "Cannot edit: no approved revision exists to base the draft on"));
            draft = copyBomRevision(lastApproved, maxRevision + 1, bom);
            draft = bomRevisionRepository.save(draft);
        }

        if (req.getDescription() != null) {
            draft.setDescription(req.getDescription());
        }
        if (req.getReasonForRevision() != null) {
            draft.setReasonForRevision(req.getReasonForRevision());
        }
        if (req.getProductionLine() != null) {
            draft.setProductionLine(req.getProductionLine());
        }
        if (req.getBomType() != null) {
            draft.setBomType(req.getBomType());
        }
        if (req.getEffectivityType() != null) {
            draft.setEffectivityType(req.getEffectivityType());
        }
        if (req.getCustomFields() != null) {
            draft.setCustomFields(req.getCustomFields());
        }
        BomRevision saved = bomRevisionRepository.save(draft);
        return BomMapper.toDto(bom, saved, true, false);
    }

    @Transactional(readOnly = true)
    public Page<BomSummaryDto> listSummaries(UUID orgId, String search, Pageable pageable) {
        String searchParam = (search == null || search.isBlank()) ? null : search;
        Page<Object[]> rows = bomRevisionRepository
                .findDisplayRevisionSummaries(orgId, searchParam, pageable);
        return rows.map(BomService::toSummaryFromRow);
    }

    @Transactional(readOnly = true)
    public BomDto getBomDisplayRevision(UUID orgId, UUID bomId) {
        Bom bom = bomRepository.findByOrgIdAndId(orgId, bomId)
                .orElseThrow(() -> new BomNotFoundException(BOM_NOT_FOUND + bomId));
        return buildDto(bom, null, null);
    }

    /** T086 — Return all BOM revisions for a BOM identity, ordered by revision ASC. */
    @Transactional(readOnly = true)
    public List<BomRevisionSummaryDto> listRevisions(UUID orgId, UUID bomId) {
        bomRepository.findByOrgIdAndId(orgId, bomId)
                .orElseThrow(() -> new BomNotFoundException(BOM_NOT_FOUND + bomId));
        return bomRevisionRepository.findByBomIdOrderByRevisionAsc(bomId)
                .stream()
                .map(BomService::toBomRevisionSummaryDto)
                .collect(Collectors.toList());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private BomRevision copyBomRevision(BomRevision source, int newRevision, Bom bom) {
        BomRevision copy = new BomRevision();
        copy.setBom(bom);
        copy.setRevision(newRevision);
        copy.setDescription(source.getDescription());
        copy.setEcoId(source.getEcoId());
        copy.setReasonForRevision(source.getReasonForRevision());
        copy.setProductionLine(source.getProductionLine());
        copy.setBomType(source.getBomType());
        copy.setEffectivityType(source.getEffectivityType());
        copy.setCustomFields(source.getCustomFields());
        return copy;
    }

    private static BomRevisionSummaryDto toBomRevisionSummaryDto(BomRevision br) {
        BomRevisionSummaryDto dto = new BomRevisionSummaryDto();
        dto.setBomRevisionId(br.getId());
        dto.setRevision(br.getRevision());
        dto.setRevisionStatus(br.getRevisionStatus());
        dto.setDescription(br.getDescription());
        dto.setSubmittedBy(br.getSubmittedBy());
        dto.setSubmittedAt(br.getSubmittedAt());
        dto.setApprovedBy(br.getApprovedBy());
        dto.setApprovedAt(br.getApprovedAt());
        dto.setCreatedBy(br.getCreatedBy());
        dto.setCreatedAt(br.getCreatedAt());
        return dto;
    }

    private BomDto buildDto(Bom bom, RevisionStatus requestedStatus, Integer revisionNumber) {
        List<BomRevision> all = bomRevisionRepository.findAllByBomId(bom.getId());
        BomRevision display;
        if (revisionNumber != null) {
            display = all.stream()
                    .filter(r -> r.getRevision().equals(revisionNumber))
                    .findFirst()
                    .orElseThrow(() -> new BomNotFoundException(
                            "No revision " + revisionNumber + " for BOM: " + bom.getId()));
        } else if (requestedStatus != null) {
            display = all.stream()
                    .filter(r -> r.getRevisionStatus() == requestedStatus)
                    .max(Comparator.comparingInt(BomRevision::getRevision))
                    .orElseThrow(() -> new BomNotFoundException(
                            "No revision with status " + requestedStatus + " for BOM: " + bom.getId()));
        } else {
            display = resolveDisplayRevision(all);
        }
        if (display == null) {
            throw new BomNotFoundException(BOM_NOT_FOUND + bom.getId());
        }
        boolean hasDraft = all.stream()
                .anyMatch(r -> r.getRevisionStatus() == RevisionStatus.DRAFT);
        boolean hasPendingApproval = all.stream()
                .anyMatch(r -> r.getRevisionStatus() == RevisionStatus.PENDING_APPROVAL);
        return BomMapper.toDto(bom, display, hasDraft, hasPendingApproval);
    }

    private BomRevision resolveDisplayRevision(Bom bom) {
        List<BomRevision> all = bomRevisionRepository.findAllByBomId(bom.getId());
        return resolveDisplayRevision(all);
    }

    private BomRevision resolveDisplayRevision(List<BomRevision> revisions) {
        for (RevisionStatus status : new RevisionStatus[]{
                RevisionStatus.APPROVED, RevisionStatus.PENDING_APPROVAL, RevisionStatus.DRAFT}) {
            var match = revisions.stream()
                    .filter(r -> r.getRevisionStatus() == status)
                    .max(Comparator.comparingInt(BomRevision::getRevision));
            if (match.isPresent()) {
                return match.get();
            }
        }
        return null;
    }

    private BomRevision getRequiredRevision(UUID orgId, UUID bomId, RevisionStatus status) {
        Bom bom = bomRepository.findByOrgIdAndId(orgId, bomId)
                .orElseThrow(() -> new BomNotFoundException(BOM_NOT_FOUND + bomId));
        return bomRevisionRepository
                .findByBomIdAndRevisionStatus(bom.getId(), status)
                .orElseThrow(() -> new BomConflictException(
                        "BOM is not in " + status + " status: " + bomId));
    }

    private void applyScalarUpdates(BomLine line, UUID bomRevisionId, UpdateBomLineRequest req) {
        if (req.getFindNumber() != null && !req.getFindNumber().equals(line.getFindNumber())) {
            if (bomLineRepository.existsByBomRevisionIdAndFindNumber(
                    bomRevisionId, req.getFindNumber())) {
                throw new BomConflictException("Find number already exists: " + req.getFindNumber());
            }
            line.setFindNumber(req.getFindNumber());
        }
        if (req.getQuantity() != null) {
            line.setQuantity(req.getQuantity());
        }
        if (req.getUnitOfMeasure() != null) {
            line.setUnitOfMeasure(req.getUnitOfMeasure());
        }
        if (req.getReferenceDesignators() != null) {
            line.setReferenceDesignators(req.getReferenceDesignators());
        }
    }

    private void applyEffectivityUpdates(BomLine line, UUID bomRevisionId, UUID lineId,
                                         UpdateBomLineRequest req) {
        boolean effectivityChanged = req.getEffectivityMethod() != null
                || req.getEffectiveFromDate() != null
                || req.getEffectiveToDate() != null;
        if (!effectivityChanged) {
            return;
        }
        LocalDate effectiveFrom = req.getEffectiveFromDate() != null
                ? req.getEffectiveFromDate() : line.getEffectiveFromDate();
        LocalDate effectiveTo = req.getEffectiveToDate() != null
                ? req.getEffectiveToDate() : line.getEffectiveToDate();
        EffectivityMethod method = req.getEffectivityMethod() != null
                ? req.getEffectivityMethod() : line.getEffectivityMethod();
        effectivityValidator.validateUpdateLine(bomRevisionId, line.getFindNumber(), method,
                effectiveFrom, effectiveTo, lineId);
        if (req.getEffectivityMethod() != null) {
            line.setEffectivityMethod(req.getEffectivityMethod());
        }
        if (req.getEffectiveFromDate() != null) {
            line.setEffectiveFromDate(req.getEffectiveFromDate());
        }
        if (req.getEffectiveToDate() != null) {
            line.setEffectiveToDate(req.getEffectiveToDate());
        }
        if (req.getEffectiveFromUnit() != null) {
            line.setEffectiveFromUnit(req.getEffectiveFromUnit());
        }
        if (req.getEffectiveToUnit() != null) {
            line.setEffectiveToUnit(req.getEffectiveToUnit());
        }
    }

    @SuppressWarnings("unchecked")
    private static BomSummaryDto toSummaryFromRow(Object[] row) {
        BomSummaryDto dto = new BomSummaryDto();
        dto.setBomId(UUID.fromString((String) row[0]));
        dto.setBomRevisionId(UUID.fromString((String) row[1]));
        dto.setRevision(((Number) row[2]).intValue());
        dto.setRevisionStatus((String) row[3]);
        dto.setDescription((String) row[4]);
        // row[5] = eco_id (not on BomSummaryDto)
        dto.setParentItemId(row[6] != null ? UUID.fromString((String) row[6]) : null);
        // row[7] = org_id (not on BomSummaryDto)
        dto.setHasDraft(Boolean.TRUE.equals(row[8]));
        dto.setCreatedBy((String) row[9]);
        Object createdAt = row[10];
        if (createdAt instanceof Timestamp ts) {
            dto.setCreatedAt(ts.toInstant());
        } else if (createdAt instanceof Instant inst) {
            dto.setCreatedAt(inst);
        }
        dto.setPartNumber((String) row[11]);
        dto.setItemRevision(row[12] != null ? ((Number) row[12]).intValue() : null);
        dto.setItemRevisionStatus((String) row[13]);
        return dto;
    }
}
