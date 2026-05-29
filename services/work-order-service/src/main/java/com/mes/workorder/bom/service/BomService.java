package com.mes.workorder.bom.service;

import com.mes.workorder.bom.api.dto.CreateBomLineRequest;
import com.mes.workorder.bom.api.dto.CreateBomRequest;
import com.mes.workorder.bom.domain.BillOfMaterials;
import com.mes.workorder.bom.domain.BomLine;
import com.mes.workorder.bom.domain.BomStatus;
import com.mes.workorder.bom.repository.BomLineRepository;
import com.mes.workorder.bom.repository.BomRepository;
import com.mes.workorder.itemmaster.repository.ItemMasterRepository;
import com.mes.workorder.bom.domain.EffectivityMethod;
import com.mes.workorder.eco.service.EcoService;
import com.mes.workorder.kafka.BomEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class BomService {

    private final BomRepository bomRepository;
    private final BomLineRepository bomLineRepository;
    private final ItemMasterRepository itemMasterRepository;
    private final BomEventPublisher bomEventPublisher;
    private final EffectivityValidator effectivityValidator;
    private final EcoService ecoService;

    public BomService(BomRepository bomRepository,
                      BomLineRepository bomLineRepository,
                      ItemMasterRepository itemMasterRepository,
                      BomEventPublisher bomEventPublisher,
                      EffectivityValidator effectivityValidator,
                      EcoService ecoService) {
        this.bomRepository = bomRepository;
        this.bomLineRepository = bomLineRepository;
        this.itemMasterRepository = itemMasterRepository;
        this.bomEventPublisher = bomEventPublisher;
        this.effectivityValidator = effectivityValidator;
        this.ecoService = ecoService;
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
                .orElseThrow(() -> new BomNotFoundException("BOM not found: " + bomId));
    }

    public BomLine addLine(UUID orgId, UUID bomId, CreateBomLineRequest req) {
        BillOfMaterials bom = bomRepository.findByOrgIdAndId(orgId, bomId)
                .orElseThrow(() -> new BomNotFoundException("BOM not found: " + bomId));

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
        return bomLineRepository.save(line);
    }

    @Transactional(readOnly = true)
    public List<BomLine> listLines(UUID orgId, UUID bomId) {
        getBom(orgId, bomId);
        return bomLineRepository.findAllByBomId(bomId);
    }

    public BillOfMaterials releaseBom(UUID orgId, UUID bomId) {
        BillOfMaterials bom = bomRepository.findByOrgIdAndId(orgId, bomId)
                .orElseThrow(() -> new BomNotFoundException("BOM not found: " + bomId));

        if (bom.getStatus() != BomStatus.DRAFT) {
            throw new BomConflictException("BOM is not in DRAFT status");
        }
        bom.setStatus(BomStatus.RELEASED);
        BillOfMaterials saved = bomRepository.save(bom);
        bomEventPublisher.publishReleased(saved);
        if (saved.getEcoId() != null) {
            ecoService.addOutputBom(saved.getEcoId(), saved.getId());
        }
        return saved;
    }
}
