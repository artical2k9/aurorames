package com.mes.workorder.itemmaster.api.dto;

import com.mes.workorder.itemmaster.domain.ItemMaster;

public final class ItemMasterMapper {

    private ItemMasterMapper() {}

    public static ItemMasterDto toDto(ItemMaster entity) {
        ItemMasterDto dto = new ItemMasterDto();
        dto.setId(entity.getId());
        dto.setOrgId(entity.getOrgId());
        dto.setPartNumber(entity.getPartNumber());
        dto.setRevision(entity.getRevision());
        dto.setDescription(entity.getDescription());
        dto.setUnitOfMeasure(entity.getUnitOfMeasure());
        dto.setCageCode(entity.getCageCode());
        dto.setClassification(entity.getClassification());
        dto.setMakeBuyCode(entity.getMakeBuyCode());
        dto.setTraceabilityMethod(entity.getTraceabilityMethod());
        dto.setShelfLifeControlled(entity.isShelfLifeControlled());
        dto.setShelfLifeDays(entity.getShelfLifeDays());
        dto.setStepPartRef(entity.getStepPartRef());
        dto.setCounterfeitRiskLevel(entity.getCounterfeitRiskLevel());
        dto.setApprovedSuppliers(entity.getApprovedSuppliers());
        dto.setVerificationRequired(entity.isVerificationRequired());
        dto.setCustomFields(entity.getCustomFields());
        dto.setStatus(entity.getStatus());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setModifiedBy(entity.getModifiedBy());
        dto.setModifiedAt(entity.getModifiedAt());
        return dto;
    }

    public static ItemMaster fromCreateRequest(CreateItemMasterRequest req) {
        ItemMaster entity = new ItemMaster();
        entity.setPartNumber(req.getPartNumber());
        entity.setRevision(req.getRevision());
        entity.setDescription(req.getDescription());
        entity.setUnitOfMeasure(req.getUnitOfMeasure());
        entity.setCageCode(req.getCageCode());
        entity.setClassification(req.getClassification());
        entity.setMakeBuyCode(req.getMakeBuyCode());
        entity.setTraceabilityMethod(req.getTraceabilityMethod());
        entity.setShelfLifeControlled(req.isShelfLifeControlled());
        entity.setShelfLifeDays(req.getShelfLifeDays());
        entity.setStepPartRef(req.getStepPartRef());
        entity.setCounterfeitRiskLevel(req.getCounterfeitRiskLevel());
        entity.setApprovedSuppliers(req.getApprovedSuppliers());
        entity.setVerificationRequired(req.isVerificationRequired());
        entity.setCustomFields(req.getCustomFields());
        return entity;
    }

    public static void applyPatch(PatchItemMasterRequest patch, ItemMaster entity) {
        if (patch.getDescription() != null) {
            entity.setDescription(patch.getDescription());
        }
        if (patch.getUnitOfMeasure() != null) {
            entity.setUnitOfMeasure(patch.getUnitOfMeasure());
        }
        if (patch.getCageCode() != null) {
            entity.setCageCode(patch.getCageCode());
        }
        if (patch.getMakeBuyCode() != null) {
            entity.setMakeBuyCode(patch.getMakeBuyCode());
        }
        if (patch.getTraceabilityMethod() != null) {
            entity.setTraceabilityMethod(patch.getTraceabilityMethod());
        }
        if (patch.getShelfLifeControlled() != null) {
            entity.setShelfLifeControlled(patch.getShelfLifeControlled());
        }
        if (patch.getShelfLifeDays() != null) {
            entity.setShelfLifeDays(patch.getShelfLifeDays());
        }
        if (patch.getStepPartRef() != null) {
            entity.setStepPartRef(patch.getStepPartRef());
        }
        if (patch.getCounterfeitRiskLevel() != null) {
            entity.setCounterfeitRiskLevel(patch.getCounterfeitRiskLevel());
        }
        if (patch.getApprovedSuppliers() != null) {
            entity.setApprovedSuppliers(patch.getApprovedSuppliers());
        }
        if (patch.getVerificationRequired() != null) {
            entity.setVerificationRequired(patch.getVerificationRequired());
        }
        if (patch.getCustomFields() != null) {
            entity.setCustomFields(patch.getCustomFields());
        }
    }
}
