package com.mes.udf.api.dto;

import com.mes.udf.domain.ModuleKey;
import com.mes.udf.domain.UdfFieldType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UdfFieldDefinitionDto {

    private UUID id;
    private UUID orgId;
    private ModuleKey moduleKey;
    private String fieldKey;
    private String label;
    private UdfFieldType fieldType;
    private boolean required;
    private String defaultValue;
    private List<String> listOptions;
    private Map<String, Object> validationRules;
    private int displayOrder;
    private boolean active;

    public UUID getId() {
        return id;
    }
    public void setId(UUID id) {
        this.id = id;
    }
    public UUID getOrgId() {
        return orgId;
    }
    public void setOrgId(UUID orgId) {
        this.orgId = orgId;
    }
    public ModuleKey getModuleKey() {
        return moduleKey;
    }
    public void setModuleKey(ModuleKey moduleKey) {
        this.moduleKey = moduleKey;
    }
    public String getFieldKey() {
        return fieldKey;
    }
    public void setFieldKey(String fieldKey) {
        this.fieldKey = fieldKey;
    }
    public String getLabel() {
        return label;
    }
    public void setLabel(String label) {
        this.label = label;
    }
    public UdfFieldType getFieldType() {
        return fieldType;
    }
    public void setFieldType(UdfFieldType fieldType) {
        this.fieldType = fieldType;
    }
    public boolean isRequired() {
        return required;
    }
    public void setRequired(boolean required) {
        this.required = required;
    }
    public String getDefaultValue() {
        return defaultValue;
    }
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }
    public List<String> getListOptions() {
        return listOptions;
    }
    public void setListOptions(List<String> listOptions) {
        this.listOptions = listOptions;
    }
    public Map<String, Object> getValidationRules() {
        return validationRules;
    }
    public void setValidationRules(Map<String, Object> validationRules) {
        this.validationRules = validationRules;
    }
    public int getDisplayOrder() {
        return displayOrder;
    }
    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
    public boolean isActive() {
        return active;
    }
    public void setActive(boolean active) {
        this.active = active;
    }
}
