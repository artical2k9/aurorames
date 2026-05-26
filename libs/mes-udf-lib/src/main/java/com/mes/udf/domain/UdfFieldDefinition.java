package com.mes.udf.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Type;
import org.hibernate.envers.Audited;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Audited
@EntityListeners(AuditingEntityListener.class)
@Table(name = "udf_field_definition")
public class UdfFieldDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Enumerated(EnumType.STRING)
    @Column(name = "module_key", nullable = false, length = 50)
    private ModuleKey moduleKey;

    @Column(name = "field_key", nullable = false, length = 100)
    private String fieldKey;

    @Column(name = "label", nullable = false, length = 255)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 15)
    private UdfFieldType fieldType;

    @Column(name = "required", nullable = false)
    private boolean required = false;

    @Column(name = "default_value", length = 500)
    private String defaultValue;

    @Type(JsonBinaryType.class)
    @Column(name = "list_options", columnDefinition = "jsonb")
    private List<String> listOptions;

    @Type(JsonBinaryType.class)
    @Column(name = "validation_rules", columnDefinition = "jsonb")
    private Map<String, Object> validationRules;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @CreatedBy
    @Column(name = "created_by", nullable = false, length = 255, updatable = false)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedBy
    @Column(name = "modified_by", nullable = false, length = 255)
    private String modifiedBy;

    @LastModifiedDate
    @Column(name = "modified_at", nullable = false)
    private Instant modifiedAt;

    // ── Getters and setters ───────────────────────────────────────────────────

    public UUID getId() {
        return id;
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
    public String getCreatedBy() {
        return createdBy;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
    public String getModifiedBy() {
        return modifiedBy;
    }
    public Instant getModifiedAt() {
        return modifiedAt;
    }
}
