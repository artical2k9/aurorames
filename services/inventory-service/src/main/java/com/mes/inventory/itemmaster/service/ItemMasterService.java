package com.mes.inventory.itemmaster.service;

import com.mes.udf.domain.ModuleKey;
import com.mes.udf.service.UdfValidator;
import com.mes.udf.service.UdfViolation;
import com.mes.inventory.itemmaster.api.dto.CreateItemMasterRequest;
import com.mes.inventory.itemmaster.api.dto.ItemMasterMapper;
import com.mes.inventory.itemmaster.api.dto.PatchItemMasterRequest;
import com.mes.inventory.itemmaster.domain.Classification;
import com.mes.inventory.itemmaster.domain.CounterfeitRiskLevel;
import com.mes.inventory.itemmaster.domain.ItemMaster;
import com.mes.inventory.itemmaster.domain.ItemStatus;
import com.mes.inventory.itemmaster.domain.MakeBuyCode;
import com.mes.inventory.itemmaster.repository.ItemMasterRepository;
import com.mes.inventory.kafka.ItemMasterEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;  // used by validateUdf

@Service
@Transactional
public class ItemMasterService {

    private final ItemMasterRepository repository;
    private final ItemMasterEventPublisher eventPublisher;
    private final UdfValidator udfValidator;

    public ItemMasterService(ItemMasterRepository repository,
                              ItemMasterEventPublisher eventPublisher,
                              UdfValidator udfValidator) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.udfValidator = udfValidator;
    }

    public ItemMaster create(UUID orgId, String partNumber, String revision, CreateItemMasterRequest req) {
        if (repository.existsByOrgIdAndPartNumberAndRevision(orgId, partNumber, revision)) {
            throw new ItemMasterConflictException(
                    "Item master already exists: " + partNumber + " Rev " + revision);
        }
        validateShelfLife(req.isShelfLifeControlled(), req.getShelfLifeDays());
        validateUdf(orgId, req.getCustomFields());

        ItemMaster entity = ItemMasterMapper.fromCreateRequest(req);
        entity.setOrgId(orgId);
        entity.setPartNumber(partNumber);
        entity.setRevision(revision);

        ItemMaster saved = repository.save(entity);
        eventPublisher.publishCreated(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public ItemMaster get(UUID orgId, UUID itemId) {
        return repository.findByOrgIdAndId(orgId, itemId)
                .orElseThrow(() -> new ItemMasterNotFoundException("Item not found: " + itemId));
    }

    public ItemMaster patch(UUID orgId, UUID itemId, PatchItemMasterRequest req) {
        ItemMaster entity = get(orgId, itemId);

        Boolean controlled = req.getShelfLifeControlled();
        Integer days = req.getShelfLifeDays();
        boolean effectiveControlled = controlled != null ? controlled : entity.isShelfLifeControlled();
        Integer effectiveDays = days != null ? days : entity.getShelfLifeDays();
        validateShelfLife(effectiveControlled, effectiveDays);
        if (req.getCustomFields() != null) {
            validateUdf(orgId, req.getCustomFields());
        }

        ItemMasterMapper.applyPatch(req, entity);

        ItemMaster saved = repository.save(entity);
        eventPublisher.publishUpdated(saved);
        return saved;
    }

    public ItemMaster obsolete(UUID orgId, UUID itemId) {
        ItemMaster entity = get(orgId, itemId);
        entity.setStatus(ItemStatus.OBSOLETE);
        ItemMaster saved = repository.save(entity);
        eventPublisher.publishUpdated(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<ItemMaster> list(UUID orgId, ItemStatus status, Classification classification,
                                 CounterfeitRiskLevel riskLevel, MakeBuyCode makeBuyCode,
                                 String search, Pageable pageable) {
        String searchPattern = (search != null && !search.isBlank())
                ? "%" + search.toLowerCase() + "%"
                : null;
        return repository.findAllFiltered(orgId, status, classification, riskLevel, makeBuyCode, searchPattern, pageable);
    }

    private void validateShelfLife(boolean controlled, Integer days) {
        if (controlled && days == null) {
            throw new ItemMasterValidationException("shelfLifeDays is required when shelfLifeControlled is true");
        }
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
