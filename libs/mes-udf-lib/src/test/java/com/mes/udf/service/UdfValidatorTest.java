package com.mes.udf.service;

import com.mes.udf.domain.ModuleKey;
import com.mes.udf.domain.UdfFieldDefinition;
import com.mes.udf.domain.UdfFieldType;
import com.mes.udf.repository.UdfFieldDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UdfValidatorTest {

    @Mock
    UdfFieldDefinitionRepository repository;

    @InjectMocks
    UdfValidator validator;

    UUID orgId = UUID.randomUUID();

    @Test
    void noDefinitionsPassesValidation() {
        when(repository.findByOrgIdAndModuleKeyAndActiveTrue(orgId, ModuleKey.ITEM_MASTER))
                .thenReturn(List.of());

        List<UdfViolation> violations = validator.validate(orgId, ModuleKey.ITEM_MASTER, Map.of());

        assertThat(violations).isEmpty();
    }

    @Test
    void textRequiredFieldMissingReturnsViolation() {
        when(repository.findByOrgIdAndModuleKeyAndActiveTrue(orgId, ModuleKey.ITEM_MASTER))
                .thenReturn(List.of(requiredTextField("drawing_ref")));

        List<UdfViolation> violations = validator.validate(orgId, ModuleKey.ITEM_MASTER, Map.of());

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).fieldKey()).isEqualTo("drawing_ref");
    }

    @Test
    void listFieldValueNotInOptionsReturnsViolation() {
        when(repository.findByOrgIdAndModuleKeyAndActiveTrue(orgId, ModuleKey.ITEM_MASTER))
                .thenReturn(List.of(listField("material_class", List.of("ALUMINUM", "STEEL"))));

        List<UdfViolation> violations = validator.validate(orgId, ModuleKey.ITEM_MASTER,
                Map.of("material_class", "TITANIUM"));

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).fieldKey()).isEqualTo("material_class");
    }

    @Test
    void numberBelowMinReturnsViolation() {
        when(repository.findByOrgIdAndModuleKeyAndActiveTrue(orgId, ModuleKey.ITEM_MASTER))
                .thenReturn(List.of(numberField("thickness_mm", 0.0, 100.0)));

        List<UdfViolation> violations = validator.validate(orgId, ModuleKey.ITEM_MASTER,
                Map.of("thickness_mm", -1));

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).fieldKey()).isEqualTo("thickness_mm");
    }

    @Test
    void numberAboveMaxReturnsViolation() {
        when(repository.findByOrgIdAndModuleKeyAndActiveTrue(orgId, ModuleKey.ITEM_MASTER))
                .thenReturn(List.of(numberField("thickness_mm", 0.0, 100.0)));

        List<UdfViolation> violations = validator.validate(orgId, ModuleKey.ITEM_MASTER,
                Map.of("thickness_mm", 150));

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).fieldKey()).isEqualTo("thickness_mm");
    }

    @Test
    void booleanStringTruePassesValidation() {
        when(repository.findByOrgIdAndModuleKeyAndActiveTrue(orgId, ModuleKey.ITEM_MASTER))
                .thenReturn(List.of(booleanField("is_critical")));

        List<UdfViolation> violations = validator.validate(orgId, ModuleKey.ITEM_MASTER,
                Map.of("is_critical", "true"));

        assertThat(violations).isEmpty();
    }

    @Test
    void booleanStringFalsePassesValidation() {
        when(repository.findByOrgIdAndModuleKeyAndActiveTrue(orgId, ModuleKey.ITEM_MASTER))
                .thenReturn(List.of(booleanField("is_critical")));

        List<UdfViolation> violations = validator.validate(orgId, ModuleKey.ITEM_MASTER,
                Map.of("is_critical", "false"));

        assertThat(violations).isEmpty();
    }

    @Test
    void invalidBooleanStringReturnsViolation() {
        when(repository.findByOrgIdAndModuleKeyAndActiveTrue(orgId, ModuleKey.ITEM_MASTER))
                .thenReturn(List.of(booleanField("is_critical")));

        List<UdfViolation> violations = validator.validate(orgId, ModuleKey.ITEM_MASTER,
                Map.of("is_critical", "maybe"));

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).fieldKey()).isEqualTo("is_critical");
    }

    @Test
    void invalidDateFormatReturnsViolation() {
        when(repository.findByOrgIdAndModuleKeyAndActiveTrue(orgId, ModuleKey.ITEM_MASTER))
                .thenReturn(List.of(dateField("expiry_date")));

        List<UdfViolation> violations = validator.validate(orgId, ModuleKey.ITEM_MASTER,
                Map.of("expiry_date", "01-01-2025"));

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).fieldKey()).isEqualTo("expiry_date");
    }

    @Test
    void validIsoDatePassesValidation() {
        when(repository.findByOrgIdAndModuleKeyAndActiveTrue(orgId, ModuleKey.ITEM_MASTER))
                .thenReturn(List.of(dateField("expiry_date")));

        List<UdfViolation> violations = validator.validate(orgId, ModuleKey.ITEM_MASTER,
                Map.of("expiry_date", "2025-12-31"));

        assertThat(violations).isEmpty();
    }

    @Test
    void optionalFieldAbsentPassesValidation() {
        when(repository.findByOrgIdAndModuleKeyAndActiveTrue(orgId, ModuleKey.ITEM_MASTER))
                .thenReturn(List.of(optionalTextField("drawing_ref")));

        List<UdfViolation> violations = validator.validate(orgId, ModuleKey.ITEM_MASTER, Map.of());

        assertThat(violations).isEmpty();
    }

    @Test
    void nullCustomFieldsWithNoRequiredDefsPassesValidation() {
        when(repository.findByOrgIdAndModuleKeyAndActiveTrue(orgId, ModuleKey.ITEM_MASTER))
                .thenReturn(List.of(optionalTextField("drawing_ref")));

        List<UdfViolation> violations = validator.validate(orgId, ModuleKey.ITEM_MASTER, null);

        assertThat(violations).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UdfFieldDefinition requiredTextField(String fieldKey) {
        var def = new UdfFieldDefinition();
        def.setFieldKey(fieldKey);
        def.setFieldType(UdfFieldType.TEXT);
        def.setModuleKey(ModuleKey.ITEM_MASTER);
        def.setRequired(true);
        return def;
    }

    private UdfFieldDefinition optionalTextField(String fieldKey) {
        var def = new UdfFieldDefinition();
        def.setFieldKey(fieldKey);
        def.setFieldType(UdfFieldType.TEXT);
        def.setModuleKey(ModuleKey.ITEM_MASTER);
        def.setRequired(false);
        return def;
    }

    private UdfFieldDefinition listField(String fieldKey, List<String> options) {
        var def = new UdfFieldDefinition();
        def.setFieldKey(fieldKey);
        def.setFieldType(UdfFieldType.LIST);
        def.setModuleKey(ModuleKey.ITEM_MASTER);
        def.setRequired(false);
        def.setListOptions(options);
        return def;
    }

    private UdfFieldDefinition numberField(String fieldKey, double min, double max) {
        var def = new UdfFieldDefinition();
        def.setFieldKey(fieldKey);
        def.setFieldType(UdfFieldType.NUMBER);
        def.setModuleKey(ModuleKey.ITEM_MASTER);
        def.setRequired(false);
        def.setValidationRules(Map.of("min", min, "max", max));
        return def;
    }

    private UdfFieldDefinition booleanField(String fieldKey) {
        var def = new UdfFieldDefinition();
        def.setFieldKey(fieldKey);
        def.setFieldType(UdfFieldType.BOOLEAN);
        def.setModuleKey(ModuleKey.ITEM_MASTER);
        def.setRequired(false);
        return def;
    }

    private UdfFieldDefinition dateField(String fieldKey) {
        var def = new UdfFieldDefinition();
        def.setFieldKey(fieldKey);
        def.setFieldType(UdfFieldType.DATE);
        def.setModuleKey(ModuleKey.ITEM_MASTER);
        def.setRequired(false);
        return def;
    }
}
