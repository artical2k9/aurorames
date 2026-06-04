package com.mes.engineering.eco.api.dto;

import com.mes.engineering.eco.domain.EngineeringChangeOrder;

public class EcoMapper {

    private EcoMapper() {}

    public static EcoDto toDto(EngineeringChangeOrder eco) {
        EcoDto dto = new EcoDto();
        dto.setId(eco.getId());
        dto.setOrgId(eco.getOrgId());
        dto.setEcoNumber(eco.getEcoNumber());
        dto.setTitle(eco.getTitle());
        dto.setDescription(eco.getDescription());
        dto.setStatus(eco.getStatus() != null ? eco.getStatus().name() : null);
        dto.setInitiatedBy(eco.getInitiatedBy());
        dto.setApprovedBy(eco.getApprovedBy());
        dto.setApprovedAt(eco.getApprovedAt());
        dto.setAffectedItemIds(eco.getAffectedItemIds());
        dto.setOutputBomIds(eco.getOutputBomIds());
        dto.setCreatedBy(eco.getCreatedBy());
        dto.setCreatedAt(eco.getCreatedAt());
        return dto;
    }
}
