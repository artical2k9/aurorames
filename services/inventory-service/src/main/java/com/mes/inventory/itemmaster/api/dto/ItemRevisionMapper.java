package com.mes.inventory.itemmaster.api.dto;

import com.mes.inventory.itemmaster.domain.Item;
import com.mes.inventory.itemmaster.domain.ItemRevision;
import com.mes.inventory.itemmaster.domain.RevisionStatus;

public final class ItemRevisionMapper {

    private ItemRevisionMapper() {}

    /**
     * Maps an ItemRevision to ItemMasterDto.
     *
     * @param ir      the revision to map
     * @param hasDraft pre-computed flag — true when any DRAFT currently exists for this item
     */
    public static ItemMasterDto toDto(ItemRevision ir, boolean hasDraft) {
        Item item = ir.getItem();
        ItemMasterDto dto = new ItemMasterDto();
        dto.setId(item.getId());
        dto.setOrgId(item.getOrgId());
        dto.setPartNumber(item.getPartNumber());
        dto.setRevisionId(ir.getId());
        dto.setRevision(ir.getRevision());
        dto.setRevisionStatus(ir.getRevisionStatus());
        dto.setHasDraft(hasDraft);
        dto.setDescription(ir.getDescription());
        dto.setUnitOfMeasure(ir.getUnitOfMeasure());
        dto.setCageCode(ir.getCageCode());
        dto.setClassification(ir.getClassification());
        dto.setMakeBuyCode(ir.getMakeBuyCode());
        dto.setTraceabilityMethod(ir.getTraceabilityMethod());
        dto.setShelfLifeControlled(ir.isShelfLifeControlled());
        dto.setShelfLifeDays(ir.getShelfLifeDays());
        dto.setStepPartRef(ir.getStepPartRef());
        dto.setCounterfeitRiskLevel(ir.getCounterfeitRiskLevel());
        dto.setApprovedSuppliers(ir.getApprovedSuppliers());
        dto.setVerificationRequired(ir.isVerificationRequired());
        dto.setCustomFields(ir.getCustomFields());
        dto.setSubmittedBy(ir.getSubmittedBy());
        dto.setSubmittedAt(ir.getSubmittedAt());
        dto.setApprovedBy(ir.getApprovedBy());
        dto.setApprovedAt(ir.getApprovedAt());
        dto.setRejectedBy(ir.getRejectedBy());
        dto.setRejectedAt(ir.getRejectedAt());
        dto.setRejectionReason(ir.getRejectionReason());
        dto.setCreatedBy(ir.getCreatedBy());
        dto.setCreatedAt(ir.getCreatedAt());
        dto.setModifiedBy(ir.getModifiedBy());
        dto.setModifiedAt(ir.getModifiedAt());
        return dto;
    }

    public static ItemRevisionSummaryDto toSummaryDto(ItemRevision ir) {
        ItemRevisionSummaryDto dto = new ItemRevisionSummaryDto();
        dto.setRevisionId(ir.getId());
        dto.setRevision(ir.getRevision());
        dto.setRevisionStatus(ir.getRevisionStatus());
        dto.setDescription(ir.getDescription());
        dto.setSubmittedBy(ir.getSubmittedBy());
        dto.setSubmittedAt(ir.getSubmittedAt());
        dto.setApprovedBy(ir.getApprovedBy());
        dto.setApprovedAt(ir.getApprovedAt());
        dto.setCreatedBy(ir.getCreatedBy());
        dto.setCreatedAt(ir.getCreatedAt());
        return dto;
    }

    public static ItemRevision fromCreateRequest(CreateItemMasterRequest req) {
        ItemRevision ir = new ItemRevision();
        ir.setRevision(0);
        ir.setRevisionStatus(RevisionStatus.DRAFT);
        applyDataFields(req, ir);
        return ir;
    }

    public static void applyPatch(PatchItemMasterRequest patch, ItemRevision ir) {
        if (patch.getDescription() != null) {
            ir.setDescription(patch.getDescription());
        }
        if (patch.getUnitOfMeasure() != null) {
            ir.setUnitOfMeasure(patch.getUnitOfMeasure());
        }
        if (patch.getCageCode() != null) {
            ir.setCageCode(patch.getCageCode());
        }
        if (patch.getMakeBuyCode() != null) {
            ir.setMakeBuyCode(patch.getMakeBuyCode());
        }
        if (patch.getTraceabilityMethod() != null) {
            ir.setTraceabilityMethod(patch.getTraceabilityMethod());
        }
        if (patch.getShelfLifeControlled() != null) {
            ir.setShelfLifeControlled(patch.getShelfLifeControlled());
        }
        if (patch.getShelfLifeDays() != null) {
            ir.setShelfLifeDays(patch.getShelfLifeDays());
        }
        if (patch.getStepPartRef() != null) {
            ir.setStepPartRef(patch.getStepPartRef());
        }
        if (patch.getCounterfeitRiskLevel() != null) {
            ir.setCounterfeitRiskLevel(patch.getCounterfeitRiskLevel());
        }
        if (patch.getApprovedSuppliers() != null) {
            ir.setApprovedSuppliers(patch.getApprovedSuppliers());
        }
        if (patch.getVerificationRequired() != null) {
            ir.setVerificationRequired(patch.getVerificationRequired());
        }
        if (patch.getCustomFields() != null) {
            ir.setCustomFields(patch.getCustomFields());
        }
    }

    private static void applyDataFields(CreateItemMasterRequest req, ItemRevision ir) {
        ir.setDescription(req.getDescription());
        ir.setUnitOfMeasure(req.getUnitOfMeasure());
        ir.setCageCode(req.getCageCode());
        ir.setClassification(req.getClassification());
        ir.setMakeBuyCode(req.getMakeBuyCode());
        ir.setTraceabilityMethod(req.getTraceabilityMethod());
        ir.setShelfLifeControlled(req.isShelfLifeControlled());
        ir.setShelfLifeDays(req.getShelfLifeDays());
        ir.setStepPartRef(req.getStepPartRef());
        ir.setCounterfeitRiskLevel(req.getCounterfeitRiskLevel());
        ir.setApprovedSuppliers(req.getApprovedSuppliers());
        ir.setVerificationRequired(req.isVerificationRequired());
        ir.setCustomFields(req.getCustomFields());
    }
}
