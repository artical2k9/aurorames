package com.mes.udf.api.dto;

import com.mes.udf.domain.ModuleKey;
import com.mes.udf.domain.UdfFieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public class CreateUdfFieldRequest {

    @NotNull
    private ModuleKey moduleKey;

    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "^[a-z][a-z0-9_]{0,99}$",
             message = "must start with a lowercase letter; only lowercase letters, digits, and underscores allowed")
    private String fieldKey;

    @NotBlank
    @Size(max = 255)
    private String label;

    @NotNull
    private UdfFieldType fieldType;

    private boolean required = false;
    private String defaultValue;
    private List<String> listOptions;
    private Map<String, Object> validationRules;
    private int displayOrder = 0;

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
}
