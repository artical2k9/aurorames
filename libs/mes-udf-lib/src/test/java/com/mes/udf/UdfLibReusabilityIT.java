package com.mes.udf;

import com.mes.udf.api.dto.CreateUdfFieldRequest;
import com.mes.udf.domain.ModuleKey;
import com.mes.udf.domain.UdfFieldDefinition;
import com.mes.udf.domain.UdfFieldType;
import com.mes.udf.repository.UdfFieldDefinitionRepository;
import com.mes.udf.service.UdfFieldDefinitionService;
import com.mes.udf.service.UdfValidator;
import com.mes.udf.service.UdfViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SC-009 — Proves mes-udf-lib is module-agnostic.
 *
 * Both UdfFieldDefinitionService and UdfValidator must work with ModuleKey.ROUTING
 * using zero code changes to the library. Changing the moduleKey constant is the
 * only caller-side difference between ITEM_MASTER and ROUTING usage.
 */
@ExtendWith(MockitoExtension.class)
class UdfLibReusabilityIT {

    @Mock
    UdfFieldDefinitionRepository repository;

    @InjectMocks
    UdfValidator validator;

    UUID orgId = UUID.fromString("00000000-0000-0000-0000-000000000099");

    // ── UdfFieldDefinitionService tests ───────────────────────────────────────

    @Test
    void defineTextFieldOnRoutingModuleSavesEntity() {
        when(repository.existsByOrgIdAndModuleKeyAndFieldKey(
                orgId, ModuleKey.ROUTING, "process_ref")).thenReturn(false);
        when(repository.save(any(UdfFieldDefinition.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UdfFieldDefinitionService service = new UdfFieldDefinitionService(
                repository, Optional.empty(), Optional.empty(), Optional.empty());

        CreateUdfFieldRequest req = routingTextField("process_ref", "Process Reference", true);
        UdfFieldDefinition saved = service.define(orgId, req);

        assertThat(saved.getModuleKey()).isEqualTo(ModuleKey.ROUTING);
        assertThat(saved.getFieldKey()).isEqualTo("process_ref");
        assertThat(saved.getFieldType()).isEqualTo(UdfFieldType.TEXT);
        assertThat(saved.isRequired()).isTrue();

        ArgumentCaptor<UdfFieldDefinition> captor =
                ArgumentCaptor.forClass(UdfFieldDefinition.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getModuleKey()).isEqualTo(ModuleKey.ROUTING);
    }

    @Test
    void listRoutingFieldsIsScopedToRoutingModuleKey() {
        UdfFieldDefinition field = routingFieldEntity("process_ref", true);
        when(repository.findByOrgIdAndModuleKeyAndActiveTrue(orgId, ModuleKey.ROUTING))
                .thenReturn(List.of(field));

        UdfFieldDefinitionService service = new UdfFieldDefinitionService(
                repository, Optional.empty(), Optional.empty(), Optional.empty());

        List<UdfFieldDefinition> results = service.list(orgId, ModuleKey.ROUTING);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getModuleKey()).isEqualTo(ModuleKey.ROUTING);
    }

    // ── UdfValidator tests ────────────────────────────────────────────────────

    @Test
    void validateRoutingRequiredFieldPresentProducesNoViolations() {
        when(repository.findByOrgIdAndModuleKeyAndActiveTrue(orgId, ModuleKey.ROUTING))
                .thenReturn(List.of(routingFieldEntity("process_ref", true)));

        List<UdfViolation> violations = validator.validate(
                orgId, ModuleKey.ROUTING, Map.of("process_ref", "PR-001"));

        assertThat(violations).isEmpty();
    }

    @Test
    void validateRoutingRequiredFieldAbsentProducesViolation() {
        when(repository.findByOrgIdAndModuleKeyAndActiveTrue(orgId, ModuleKey.ROUTING))
                .thenReturn(List.of(routingFieldEntity("process_ref", true)));

        List<UdfViolation> violations = validator.validate(
                orgId, ModuleKey.ROUTING, Map.of());

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).fieldKey()).isEqualTo("process_ref");
    }

    @Test
    void routingAndItemMasterDefinitionsAreIndependent() {
        // ROUTING has a required field; ITEM_MASTER has no definitions for this org.
        when(repository.findByOrgIdAndModuleKeyAndActiveTrue(orgId, ModuleKey.ROUTING))
                .thenReturn(List.of(routingFieldEntity("process_ref", true)));
        when(repository.findByOrgIdAndModuleKeyAndActiveTrue(orgId, ModuleKey.ITEM_MASTER))
                .thenReturn(List.of());

        // ROUTING validation fails when field absent
        List<UdfViolation> routingViolations = validator.validate(
                orgId, ModuleKey.ROUTING, Map.of());
        assertThat(routingViolations).hasSize(1);

        // ITEM_MASTER validation passes with the same empty customFields map
        List<UdfViolation> itemMasterViolations = validator.validate(
                orgId, ModuleKey.ITEM_MASTER, Map.of());
        assertThat(itemMasterViolations).isEmpty();
    }

    @Test
    void noDefinitionsForRoutingModuleAlwaysPasses() {
        when(repository.findByOrgIdAndModuleKeyAndActiveTrue(orgId, ModuleKey.ROUTING))
                .thenReturn(List.of());

        List<UdfViolation> violations = validator.validate(
                orgId, ModuleKey.ROUTING, Map.of());

        assertThat(violations).isEmpty();
    }

    // ── BOM_LINE module tests (T194) ──────────────────────────────────────────

    @Test
    void defineTextFieldOnBomLineModuleSavesEntity() {
        when(repository.existsByOrgIdAndModuleKeyAndFieldKey(
                orgId, ModuleKey.BOM_LINE, "ref_des")).thenReturn(false);
        when(repository.save(any(UdfFieldDefinition.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UdfFieldDefinitionService service = new UdfFieldDefinitionService(
                repository, Optional.empty(), Optional.empty(), Optional.empty());

        CreateUdfFieldRequest req = bomLineTextField("ref_des", "Reference Designator", false);
        UdfFieldDefinition saved = service.define(orgId, req);

        assertThat(saved.getModuleKey()).isEqualTo(ModuleKey.BOM_LINE);
        assertThat(saved.getFieldKey()).isEqualTo("ref_des");
    }

    @Test
    void defineTextFieldOnBomHeaderModuleSavesEntity() {
        when(repository.existsByOrgIdAndModuleKeyAndFieldKey(
                orgId, ModuleKey.BOM_HEADER, "drawing_ref")).thenReturn(false);
        when(repository.save(any(UdfFieldDefinition.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UdfFieldDefinitionService service = new UdfFieldDefinitionService(
                repository, Optional.empty(), Optional.empty(), Optional.empty());

        CreateUdfFieldRequest req = bomHeaderTextField("drawing_ref", "Drawing Reference", false);
        UdfFieldDefinition saved = service.define(orgId, req);

        assertThat(saved.getModuleKey()).isEqualTo(ModuleKey.BOM_HEADER);
        assertThat(saved.getFieldKey()).isEqualTo("drawing_ref");
    }

    @Test
    void validateBomLineRequiredFieldAbsentProducesViolation() {
        when(repository.findByOrgIdAndModuleKeyAndActiveTrue(orgId, ModuleKey.BOM_LINE))
                .thenReturn(List.of(bomLineFieldEntity("ref_des", true)));

        List<UdfViolation> violations = validator.validate(
                orgId, ModuleKey.BOM_LINE, Map.of());

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).fieldKey()).isEqualTo("ref_des");
    }

    @Test
    void bomLineAndBomHeaderDefinitionsAreIndependent() {
        when(repository.findByOrgIdAndModuleKeyAndActiveTrue(orgId, ModuleKey.BOM_LINE))
                .thenReturn(List.of(bomLineFieldEntity("ref_des", true)));
        when(repository.findByOrgIdAndModuleKeyAndActiveTrue(orgId, ModuleKey.BOM_HEADER))
                .thenReturn(List.of());

        List<UdfViolation> lineViolations = validator.validate(
                orgId, ModuleKey.BOM_LINE, Map.of());
        assertThat(lineViolations).hasSize(1);

        List<UdfViolation> headerViolations = validator.validate(
                orgId, ModuleKey.BOM_HEADER, Map.of());
        assertThat(headerViolations).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private CreateUdfFieldRequest routingTextField(String fieldKey, String label, boolean required) {
        CreateUdfFieldRequest req = new CreateUdfFieldRequest();
        req.setModuleKey(ModuleKey.ROUTING);
        req.setFieldKey(fieldKey);
        req.setLabel(label);
        req.setFieldType(UdfFieldType.TEXT);
        req.setRequired(required);
        req.setDisplayOrder(0);
        return req;
    }

    private UdfFieldDefinition routingFieldEntity(String fieldKey, boolean required) {
        UdfFieldDefinition def = new UdfFieldDefinition();
        def.setModuleKey(ModuleKey.ROUTING);
        def.setFieldKey(fieldKey);
        def.setFieldType(UdfFieldType.TEXT);
        def.setRequired(required);
        def.setActive(true);
        return def;
    }

    private CreateUdfFieldRequest bomLineTextField(String fieldKey, String label, boolean required) {
        CreateUdfFieldRequest req = new CreateUdfFieldRequest();
        req.setModuleKey(ModuleKey.BOM_LINE);
        req.setFieldKey(fieldKey);
        req.setLabel(label);
        req.setFieldType(UdfFieldType.TEXT);
        req.setRequired(required);
        req.setDisplayOrder(0);
        return req;
    }

    private CreateUdfFieldRequest bomHeaderTextField(String fieldKey, String label, boolean required) {
        CreateUdfFieldRequest req = new CreateUdfFieldRequest();
        req.setModuleKey(ModuleKey.BOM_HEADER);
        req.setFieldKey(fieldKey);
        req.setLabel(label);
        req.setFieldType(UdfFieldType.TEXT);
        req.setRequired(required);
        req.setDisplayOrder(0);
        return req;
    }

    private UdfFieldDefinition bomLineFieldEntity(String fieldKey, boolean required) {
        UdfFieldDefinition def = new UdfFieldDefinition();
        def.setModuleKey(ModuleKey.BOM_LINE);
        def.setFieldKey(fieldKey);
        def.setFieldType(UdfFieldType.TEXT);
        def.setRequired(required);
        def.setActive(true);
        return def;
    }
}
