package com.mes.udf.service;

import com.mes.udf.api.dto.CreateUdfFieldRequest;
import com.mes.udf.api.dto.UdfFieldDefinitionMapper;
import com.mes.udf.domain.ModuleKey;
import com.mes.udf.domain.UdfFieldDefinition;
import com.mes.udf.repository.UdfFieldDefinitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class UdfFieldDefinitionService {

    private final UdfFieldDefinitionRepository repository;
    private final Optional<UdfUsageChecker> usageChecker;
    private final Optional<UdfValueNullifier> valueNullifier;

    public UdfFieldDefinitionService(UdfFieldDefinitionRepository repository,
                                     Optional<UdfUsageChecker> usageChecker,
                                     Optional<UdfValueNullifier> valueNullifier) {
        this.repository = repository;
        this.usageChecker = usageChecker;
        this.valueNullifier = valueNullifier;
    }

    public UdfFieldDefinition define(UUID orgId, CreateUdfFieldRequest req) {
        if (repository.existsByOrgIdAndModuleKeyAndFieldKey(
                orgId, req.getModuleKey(), req.getFieldKey())) {
            throw new UdfDefinitionConflictException(
                    "UDF field already defined: " + req.getFieldKey()
                    + " on module " + req.getModuleKey());
        }
        UdfFieldDefinition entity = UdfFieldDefinitionMapper.fromRequest(orgId, req);
        return repository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<UdfFieldDefinition> list(UUID orgId, ModuleKey moduleKey) {
        return repository.findByOrgIdAndModuleKeyAndActiveTrue(orgId, moduleKey);
    }

    public void deactivate(UUID orgId, UUID fieldId, boolean force) {
        UdfFieldDefinition def = repository.findByOrgIdAndId(orgId, fieldId)
                .orElseThrow(() -> new UdfDefinitionConflictException(
                        "UDF field not found: " + fieldId));

        long usageCount = usageChecker
                .map(c -> c.countUsages(orgId, def.getFieldKey()))
                .orElse(0L);

        if (!force && usageCount > 0) {
            throw new UdfDefinitionConflictException(
                    "Cannot delete field '" + def.getFieldKey() + "': "
                    + usageCount + " record(s) have values. Use force=true to nullify.");
        }

        if (force && usageCount > 0) {
            valueNullifier.ifPresent(n -> n.nullifyValues(orgId, def.getFieldKey()));
        }

        def.setActive(false);
        repository.save(def);
    }
}
