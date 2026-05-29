package com.mes.udf.api.dto;

import com.mes.udf.domain.UdfFieldDefinition;

import java.util.UUID;

public final class UdfFieldDefinitionMapper {

    private UdfFieldDefinitionMapper() {}

    public static UdfFieldDefinitionDto toDto(UdfFieldDefinition entity) {
        var dto = new UdfFieldDefinitionDto();
        dto.setId(entity.getId());
        dto.setModuleKey(entity.getModuleKey());
        dto.setFieldKey(entity.getFieldKey());
        dto.setLabel(entity.getLabel());
        dto.setFieldType(entity.getFieldType());
        dto.setRequired(entity.isRequired());
        dto.setDefaultValue(entity.getDefaultValue());
        dto.setListOptions(entity.getListOptions());
        dto.setValidationRules(entity.getValidationRules());
        dto.setDisplayOrder(entity.getDisplayOrder());
        dto.setActive(entity.isActive());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setModifiedAt(entity.getModifiedAt());
        return dto;
    }

    public static UdfFieldDefinition fromRequest(UUID orgId, CreateUdfFieldRequest req) {
        var entity = new UdfFieldDefinition();
        entity.setOrgId(orgId);
        entity.setModuleKey(req.getModuleKey());
        entity.setFieldKey(req.getFieldKey());
        entity.setLabel(req.getLabel());
        entity.setFieldType(req.getFieldType());
        entity.setRequired(req.isRequired());
        entity.setDefaultValue(req.getDefaultValue());
        entity.setListOptions(req.getListOptions());
        entity.setValidationRules(req.getValidationRules());
        entity.setDisplayOrder(req.getDisplayOrder());
        return entity;
    }

}
