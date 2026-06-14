package com.mes.inventory.bom.api.dto;

import com.mes.inventory.bom.domain.Bom;
import com.mes.inventory.bom.domain.BomLine;
import com.mes.inventory.bom.domain.BomRevision;
import com.mes.inventory.itemmaster.domain.CounterfeitRiskLevel;
import com.mes.inventory.itemmaster.domain.ItemRevision;

public final class BomMapper {

    private BomMapper() {
    }

    public static BomDto toDto(Bom bom, BomRevision br, boolean hasDraft, boolean hasPendingApproval) {
        BomDto dto = new BomDto();
        dto.setId(bom.getId());
        dto.setOrgId(bom.getOrgId());
        dto.setParentItemId(bom.getParentItemId());
        dto.setBomRevisionId(br.getId());
        dto.setRevision(br.getRevision());
        dto.setRevisionStatus(br.getRevisionStatus().name());
        dto.setHasDraft(hasDraft);
        dto.setHasPendingApproval(hasPendingApproval);
        dto.setDescription(br.getDescription());
        dto.setEcoId(br.getEcoId());
        dto.setReasonForRevision(br.getReasonForRevision());
        dto.setProductionLine(br.getProductionLine());
        dto.setBomType(br.getBomType());
        dto.setEffectivityType(br.getEffectivityType());
        dto.setCustomFields(br.getCustomFields());
        dto.setSubmittedBy(br.getSubmittedBy());
        dto.setSubmittedAt(br.getSubmittedAt());
        dto.setApprovedBy(br.getApprovedBy());
        dto.setApprovedAt(br.getApprovedAt());
        dto.setRejectedBy(br.getRejectedBy());
        dto.setRejectedAt(br.getRejectedAt());
        dto.setRejectionReason(br.getRejectionReason());
        dto.setCreatedBy(br.getCreatedBy());
        dto.setCreatedAt(br.getCreatedAt());
        dto.setModifiedBy(br.getModifiedBy());
        dto.setModifiedAt(br.getModifiedAt());
        return dto;
    }

    public static BomLineDto toLineDto(BomLine line) {
        BomLineDto dto = new BomLineDto();
        dto.setId(line.getId());
        dto.setBomRevisionId(line.getBomRevision().getId());
        dto.setComponentItemRevisionId(line.getComponentItemRevision().getId());
        dto.setComponentRevision(line.getComponentItemRevision().getRevision());
        dto.setQuantity(line.getQuantity());
        dto.setUnitOfMeasure(line.getUnitOfMeasure());
        dto.setFindNumber(line.getFindNumber());
        dto.setReferenceDesignators(line.getReferenceDesignators());
        if (line.getEffectivityMethod() != null) {
            dto.setEffectivityMethod(line.getEffectivityMethod().name());
        }
        dto.setEffectiveFromDate(line.getEffectiveFromDate());
        dto.setEffectiveToDate(line.getEffectiveToDate());
        dto.setEffectiveFromUnit(line.getEffectiveFromUnit());
        dto.setEffectiveToUnit(line.getEffectiveToUnit());
        dto.setCreatedBy(line.getCreatedBy());
        dto.setCreatedAt(line.getCreatedAt());
        dto.setCustomFields(line.getCustomFields());

        ItemRevision ir = line.getComponentItemRevision();
        if (ir != null) {
            dto.setPartNumber(ir.getItem().getPartNumber());
            dto.setDescription(ir.getDescription());
            dto.setMakeBuyCode(ir.getMakeBuyCode() != null ? ir.getMakeBuyCode().name() : null);
            CounterfeitRiskLevel risk = ir.getCounterfeitRiskLevel();
            dto.setCounterfeitRiskAlert(risk == CounterfeitRiskLevel.HIGH
                    || risk == CounterfeitRiskLevel.CRITICAL);
        }
        return dto;
    }
}
