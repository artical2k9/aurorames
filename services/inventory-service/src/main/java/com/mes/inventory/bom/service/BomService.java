package com.mes.inventory.bom.service;

import com.mes.inventory.bom.api.dto.BomLineDto;
import com.mes.inventory.bom.api.dto.BomMapper;
import com.mes.inventory.bom.api.dto.BomSummaryDto;
import com.mes.inventory.bom.api.dto.CreateBomLineRequest;
import com.mes.inventory.bom.api.dto.CreateBomRequest;
import com.mes.inventory.bom.api.dto.PatchBomHeaderRequest;
import com.mes.inventory.bom.api.dto.UpdateBomLineRequest;
import com.mes.inventory.bom.domain.BillOfMaterials;
import com.mes.inventory.bom.domain.BomLine;
import com.mes.inventory.bom.domain.BomStatus;
import com.mes.inventory.bom.domain.EffectivityMethod;
import com.mes.inventory.bom.repository.BomLineRepository;
import com.mes.inventory.bom.repository.BomRepository;
import com.mes.inventory.itemmaster.domain.CounterfeitRiskLevel;
import com.mes.inventory.itemmaster.domain.ItemMaster;
import com.mes.inventory.itemmaster.repository.ItemMasterRepository;
import com.mes.inventory.kafka.BomEventPublisher;
import com.mes.inventory.kafka.ItemMasterEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@Service
@Transactional
public class BomService {

    private static final String BOM_NOT_FOUND = "BOM not found: ";

    private final BomRepository bomRepository;
    private final BomLineRepository bomLineRepository;
    private final ItemMasterRepository itemMasterRepository;
    private final BomEventPublisher bomEventPublisher;
    private final ItemMasterEventPublisher itemMasterEventPublisher;
    private final EffectivityValidator effectivityValidator;

    public BomService(BomRepository bomRepository,
                      BomLineRepository bomLineRepository,
                      ItemMasterRepository itemMasterRepository,
                      BomEventPublisher bomEventPublisher,
                      ItemMasterEventPublisher itemMasterEventPublisher,
                      EffectivityValidator effectivityValidator) {
        this.bomRepository = bomRepository;
        this.bomLineRepository = bomLineRepository;
        this.itemMasterRepository = itemMasterRepository;
        this.bomEventPublisher = bomEventPublisher;
        this.itemMasterEventPublisher = itemMasterEventPublisher;
        this.effectivityValidator = effectivityValidator;
    }

    public BillOfMaterials createBom(UUID orgId, CreateBomRequest req) {
        if (!itemMasterRepository.existsByOrgIdAndId(orgId, req.getParentItemId())) {
            throw new BomValidationException("Parent item not found: " + req.getParentItemId());
        }
        if (bomRepository.existsByOrgIdAndParentItemIdAndBomRevision(
                orgId, req.getParentItemId(), req.getBomRevision())) {
            throw new BomConflictException(
                    "BOM already exists for item " + req.getParentItemId()
                    + " revision " + req.getBomRevision());
        }
        BillOfMaterials bom = new BillOfMaterials();
        bom.setOrgId(orgId);
        bom.setParentItemId(req.getParentItemId());
        bom.setBomRevision(req.getBomRevision());
        bom.setDescription(req.getDescription());
        bom.setEcoId(req.getEcoId());
        return bomRepository.save(bom);
    }

    @Transactional(readOnly = true)
    public BillOfMaterials getBom(UUID orgId, UUID bomId) {
        return bomRepository.findByOrgIdAndId(orgId, bomId)
                .orElseThrow(() -> new BomNotFoundException(BOM_NOT_FOUND + bomId));
    }

    public BomLine addLine(UUID orgId, UUID bomId, CreateBomLineRequest req) {
        BillOfMaterials bom = bomRepository.findByOrgIdAndId(orgId, bomId)
                .orElseThrow(() -> new BomNotFoundException(BOM_NOT_FOUND + bomId));

        if (bom.getStatus() != BomStatus.DRAFT) {
            throw new BomConflictException("Cannot modify a BOM that is not in DRAFT status");
        }
        if (!itemMasterRepository.existsByOrgIdAndId(orgId, req.getComponentItemId())) {
            throw new BomValidationException("Component item not found: " + req.getComponentItemId());
        }

        EffectivityMethod method = req.getEffectivityMethod();
        if (method == EffectivityMethod.DATE) {
            if (req.getEffectiveFromDate() == null) {
                throw new BomValidationException("effectiveFromDate is required for DATE effectivity");
            }
            effectivityValidator.validateNewLine(bomId, req);
        } else if (method == EffectivityMethod.UNIT) {
            if (req.getEffectiveFromUnit() == null) {
                throw new BomValidationException("effectiveFromUnit is required for UNIT effectivity");
            }
            if (bomLineRepository.existsByBomIdAndFindNumber(bomId, req.getFindNumber())) {
                throw new BomConflictException("Find number already exists: " + req.getFindNumber());
            }
        } else {
            if (bomLineRepository.existsByBomIdAndFindNumber(bomId, req.getFindNumber())) {
                throw new BomConflictException("Find number already exists: " + req.getFindNumber());
            }
        }

        if (bomRepository.hasAncestorCycle(bomId, req.getComponentItemId())) {
            throw new BomValidationException("Adding this component would create a circular reference");
        }

        BomLine line = new BomLine();
        line.setBomId(bomId);
        line.setComponentItemId(req.getComponentItemId());
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

        itemMasterRepository.findByOrgIdAndId(orgId, req.getComponentItemId()).ifPresent(component -> {
            CounterfeitRiskLevel risk = component.getCounterfeitRiskLevel();
            if (risk == CounterfeitRiskLevel.HIGH || risk == CounterfeitRiskLevel.CRITICAL) {
                itemMasterEventPublisher.publishAs5553RiskAdded(component);
            }
        });

        return saved;
    }

    @Transactional(readOnly = true)
    public List<BomLine> listLines(UUID orgId, UUID bomId) {
        getBom(orgId, bomId);
        return bomLineRepository.findAllByBomId(bomId);
    }

    @Transactional(readOnly = true)
    public List<BomLineDto> listEnrichedLines(UUID orgId, UUID bomId) {
        getBom(orgId, bomId);
        List<BomLine> lines = bomLineRepository.findAllByBomId(bomId);
        if (lines.isEmpty()) {
            return List.of();
        }
        Set<UUID> itemIds = lines.stream().map(BomLine::getComponentItemId).collect(Collectors.toSet());
        Map<UUID, ItemMaster> itemMap = itemMasterRepository.findAllById(itemIds)
                .stream().collect(Collectors.toMap(ItemMaster::getId, i -> i));
        return lines.stream()
                .map(l -> BomMapper.toLineDto(l, itemMap.get(l.getComponentItemId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public BomLineDto enrichLine(BomLine line) {
        ItemMaster item = itemMasterRepository.findById(line.getComponentItemId()).orElse(null);
        return BomMapper.toLineDto(line, item);
    }

    public BomLine updateLine(UUID orgId, UUID bomId, UUID lineId, UpdateBomLineRequest req) {
        BillOfMaterials bom = bomRepository.findByOrgIdAndId(orgId, bomId)
                .orElseThrow(() -> new BomNotFoundException(BOM_NOT_FOUND + bomId));
        if (bom.getStatus() != BomStatus.DRAFT) {
            throw new BomConflictException("Cannot modify a BOM that is not in DRAFT status");
        }
        BomLine line = bomLineRepository.findById(lineId)
                .filter(l -> l.getBomId().equals(bomId))
                .orElseThrow(() -> new BomNotFoundException("BOM line not found: " + lineId));
        applyScalarUpdates(line, bomId, req);
        applyEffectivityUpdates(line, bomId, lineId, req);
        return bomLineRepository.save(line);
    }

    public BillOfMaterials releaseBom(UUID orgId, UUID bomId, String releasedBy) {
        BillOfMaterials bom = bomRepository.findByOrgIdAndId(orgId, bomId)
                .orElseThrow(() -> new BomNotFoundException(BOM_NOT_FOUND + bomId));
        if (bom.getStatus() != BomStatus.DRAFT) {
            throw new BomConflictException("BOM is not in DRAFT status");
        }
        bom.setStatus(BomStatus.RELEASED);
        bom.setReleasedBy(releasedBy);
        bom.setReleasedAt(Instant.now());
        BillOfMaterials saved = bomRepository.save(bom);
        // Publish bom.released event — engineering-service consumes this to link ECO output BOMs.
        // No direct cross-service call; ecoId is forwarded in the event payload for correlation.
        bomEventPublisher.publishReleased(saved);
        return saved;
    }

    private void applyScalarUpdates(BomLine line, UUID bomId, UpdateBomLineRequest req) {
        if (req.getFindNumber() != null && !req.getFindNumber().equals(line.getFindNumber())) {
            if (bomLineRepository.existsByBomIdAndFindNumber(bomId, req.getFindNumber())) {
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

    @Transactional(readOnly = true)
    public List<BillOfMaterials> listByItem(UUID orgId, UUID parentItemId) {
        return bomRepository.findAllByOrgIdAndParentItemId(orgId, parentItemId);
    }

    @Transactional(readOnly = true)
    public Page<BomSummaryDto> listSummaries(UUID orgId, String search, Pageable pageable) {
        Page<BillOfMaterials> page = bomRepository.searchAllByOrgId(
                orgId, search == null || search.isBlank() ? null : search, pageable);

        Set<UUID> itemIds = page.stream().map(BillOfMaterials::getParentItemId).collect(Collectors.toSet());
        Map<UUID, ItemMaster> itemMap = itemMasterRepository.findAllById(itemIds)
                .stream().collect(Collectors.toMap(ItemMaster::getId, im -> im));

        return page.map(bom -> toSummary(bom, itemMap.get(bom.getParentItemId())));
    }

    private static BomSummaryDto toSummary(BillOfMaterials bom, ItemMaster item) {
        BomSummaryDto dto = new BomSummaryDto();
        dto.setBomId(bom.getId());
        dto.setBomRevision(bom.getBomRevision());
        dto.setBomStatus(bom.getStatus() != null ? bom.getStatus().name() : null);
        dto.setBomDescription(bom.getDescription());
        dto.setParentItemId(bom.getParentItemId());
        dto.setCreatedBy(bom.getCreatedBy());
        dto.setCreatedAt(bom.getCreatedAt());
        if (item != null) {
            dto.setPartNumber(item.getPartNumber());
            dto.setRevision(item.getRevision());
            dto.setItemDescription(item.getDescription());
            dto.setClassification(item.getClassification() != null ? item.getClassification().name() : null);
            dto.setUnitOfMeasure(item.getUnitOfMeasure());
            dto.setItemStatus(item.getStatus() != null ? item.getStatus().name() : null);
        }
        return dto;
    }

    @Transactional(readOnly = true)
    public Page<BillOfMaterials> list(UUID orgId, Pageable pageable) {
        List<BillOfMaterials> all = bomRepository.findAll().stream()
                .filter(b -> b.getOrgId().equals(orgId)).toList();
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), all.size());
        List<BillOfMaterials> slice = start >= all.size() ? List.of() : all.subList(start, end);
        return new PageImpl<>(slice, pageable, all.size());
    }

    public void deleteLine(UUID orgId, UUID bomId, UUID lineId) {
        BillOfMaterials bom = bomRepository.findByOrgIdAndId(orgId, bomId)
                .orElseThrow(() -> new BomNotFoundException(BOM_NOT_FOUND + bomId));
        if (bom.getStatus() != BomStatus.DRAFT) {
            throw new BomConflictException("Cannot modify a BOM that is not in DRAFT status");
        }
        BomLine line = bomLineRepository.findById(lineId)
                .filter(l -> l.getBomId().equals(bomId))
                .orElseThrow(() -> new BomNotFoundException("BOM line not found: " + lineId));
        bomLineRepository.delete(line);
    }

    public BillOfMaterials patchHeader(UUID orgId, UUID bomId, PatchBomHeaderRequest req) {
        BillOfMaterials bom = bomRepository.findByOrgIdAndId(orgId, bomId)
                .orElseThrow(() -> new BomNotFoundException(BOM_NOT_FOUND + bomId));
        if (req.getDescription() != null) {
            bom.setDescription(req.getDescription());
        }
        if (req.getReasonForRevision() != null) {
            bom.setReasonForRevision(req.getReasonForRevision());
        }
        if (req.getProductionLine() != null) {
            bom.setProductionLine(req.getProductionLine());
        }
        if (req.getBomType() != null) {
            bom.setBomType(req.getBomType());
        }
        if (req.getEffectivityType() != null) {
            bom.setEffectivityType(req.getEffectivityType());
        }
        if (req.getCustomFields() != null) {
            bom.setCustomFields(req.getCustomFields());
        }
        return bomRepository.save(bom);
    }

    private void applyEffectivityUpdates(BomLine line, UUID bomId, UUID lineId, UpdateBomLineRequest req) {
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
        effectivityValidator.validateUpdateLine(bomId, line.getFindNumber(), method,
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
}
