package com.mes.workorder.bom.api.dto;

import com.mes.workorder.bom.domain.BillOfMaterials;
import com.mes.workorder.bom.domain.BomLine;
import com.mes.workorder.itemmaster.domain.CounterfeitRiskLevel;
import com.mes.workorder.itemmaster.domain.ItemMaster;
import com.mes.workorder.itemmaster.domain.ItemStatus;

public final class BomMapper {

    private BomMapper() {
    }

    public static BomDto toDto(BillOfMaterials bom) {
        BomDto dto = new BomDto();
        dto.setId(bom.getId());
        dto.setOrgId(bom.getOrgId());
        dto.setParentItemId(bom.getParentItemId());
        dto.setBomRevision(bom.getBomRevision());
        dto.setStatus(bom.getStatus().name());
        dto.setDescription(bom.getDescription());
        dto.setEcoId(bom.getEcoId());
        dto.setCreatedBy(bom.getCreatedBy());
        dto.setCreatedAt(bom.getCreatedAt());
        dto.setReasonForRevision(bom.getReasonForRevision());
        dto.setProductionLine(bom.getProductionLine());
        dto.setBomType(bom.getBomType());
        dto.setEffectivityType(bom.getEffectivityType());
        dto.setCustomFields(bom.getCustomFields());
        return dto;
    }

    public static BomLineDto toLineDto(BomLine line) {
        BomLineDto dto = new BomLineDto();
        dto.setId(line.getId());
        dto.setBomId(line.getBomId());
        dto.setComponentItemId(line.getComponentItemId());
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
        return dto;
    }

    public static BomLineDto toLineDto(BomLine line, ItemMaster item) {
        BomLineDto dto = toLineDto(line);
        if (item != null) {
            dto.setPartNumber(item.getPartNumber());
            dto.setRevision(item.getRevision());
            dto.setDescription(item.getDescription());
            dto.setMakeBuyCode(item.getMakeBuyCode() != null ? item.getMakeBuyCode().name() : null);
            CounterfeitRiskLevel risk = item.getCounterfeitRiskLevel();
            dto.setCounterfeitRiskAlert(risk == CounterfeitRiskLevel.HIGH || risk == CounterfeitRiskLevel.CRITICAL);
            dto.setComponentObsoleted(item.getStatus() == ItemStatus.OBSOLETE);
        }
        return dto;
    }
}
