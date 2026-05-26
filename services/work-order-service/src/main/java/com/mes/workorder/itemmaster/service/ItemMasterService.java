package com.mes.workorder.itemmaster.service;

import com.mes.workorder.itemmaster.api.dto.CreateItemMasterRequest;
import com.mes.workorder.itemmaster.api.dto.ItemMasterMapper;
import com.mes.workorder.itemmaster.api.dto.PatchItemMasterRequest;
import com.mes.workorder.itemmaster.domain.Classification;
import com.mes.workorder.itemmaster.domain.ItemMaster;
import com.mes.workorder.itemmaster.domain.ItemStatus;
import com.mes.workorder.itemmaster.repository.ItemMasterRepository;
import com.mes.workorder.kafka.ItemMasterEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class ItemMasterService {

    private final ItemMasterRepository repository;
    private final ItemMasterEventPublisher eventPublisher;

    public ItemMasterService(ItemMasterRepository repository,
                              @Lazy ItemMasterEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    public ItemMaster create(UUID orgId, String partNumber, String revision, CreateItemMasterRequest req) {
        if (repository.existsByOrgIdAndPartNumberAndRevision(orgId, partNumber, revision)) {
            throw new ItemMasterConflictException(
                    "Item master already exists: " + partNumber + " Rev " + revision);
        }
        validateShelfLife(req.isShelfLifeControlled(), req.getShelfLifeDays());

        ItemMaster entity = ItemMasterMapper.fromCreateRequest(req);
        entity.setOrgId(orgId);
        entity.setPartNumber(partNumber);
        entity.setRevision(revision);
        entity.setCreatedBy(currentUser());
        entity.setModifiedBy(currentUser());

        ItemMaster saved = repository.save(entity);
        if (eventPublisher != null) {
            eventPublisher.publishCreated(saved);
        }
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

        ItemMasterMapper.applyPatch(req, entity);
        entity.setModifiedBy(currentUser());

        ItemMaster saved = repository.save(entity);
        if (eventPublisher != null) {
            eventPublisher.publishUpdated(saved);
        }
        return saved;
    }

    public ItemMaster obsolete(UUID orgId, UUID itemId) {
        ItemMaster entity = get(orgId, itemId);
        entity.setStatus(ItemStatus.OBSOLETE);
        entity.setModifiedBy(currentUser());
        return repository.save(entity);
    }

    @Transactional(readOnly = true)
    public Page<ItemMaster> list(UUID orgId, ItemStatus status, Classification classification, Pageable pageable) {
        if (status != null && classification != null) {
            return repository.findAllByOrgIdAndStatusAndClassification(orgId, status, classification, pageable);
        } else if (status != null) {
            return repository.findAllByOrgIdAndStatus(orgId, status, pageable);
        } else if (classification != null) {
            return repository.findAllByOrgIdAndClassification(orgId, classification, pageable);
        }
        return repository.findAllByOrgId(orgId, pageable);
    }

    private void validateShelfLife(boolean controlled, Integer days) {
        if (controlled && days == null) {
            throw new ItemMasterValidationException("shelfLifeDays is required when shelfLifeControlled is true");
        }
    }

    private String currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
