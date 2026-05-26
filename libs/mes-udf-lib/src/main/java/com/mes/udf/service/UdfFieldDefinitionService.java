package com.mes.udf.service;

import com.mes.udf.api.dto.CreateUdfFieldRequest;
import com.mes.udf.api.dto.UdfFieldDefinitionMapper;
import com.mes.udf.domain.ModuleKey;
import com.mes.udf.domain.UdfFieldDefinition;
import com.mes.udf.domain.UdfFieldType;
import com.mes.udf.repository.UdfFieldDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class UdfFieldDefinitionService {

    private static final Logger LOG = LoggerFactory.getLogger(UdfFieldDefinitionService.class);

    private final UdfFieldDefinitionRepository repository;
    private final Optional<UdfUsageChecker> usageChecker;
    private final Optional<UdfValueNullifier> valueNullifier;
    private final Optional<UdfAffectedRecordsFinder> affectedRecordsFinder;

    public UdfFieldDefinitionService(UdfFieldDefinitionRepository repository,
                                     Optional<UdfUsageChecker> usageChecker,
                                     Optional<UdfValueNullifier> valueNullifier,
                                     Optional<UdfAffectedRecordsFinder> affectedRecordsFinder) {
        this.repository = repository;
        this.usageChecker = usageChecker;
        this.valueNullifier = valueNullifier;
        this.affectedRecordsFinder = affectedRecordsFinder;
    }

    public UdfFieldDefinition define(UUID orgId, CreateUdfFieldRequest req) {
        if (repository.existsByOrgIdAndModuleKeyAndFieldKey(
                orgId, req.getModuleKey(), req.getFieldKey())) {
            throw new UdfDefinitionConflictException(
                    "UDF field already defined: " + req.getFieldKey()
                    + " on module " + req.getModuleKey());
        }
        if (req.getFieldType() == UdfFieldType.LIST
                && (req.getListOptions() == null || req.getListOptions().isEmpty())) {
            throw new UdfValidationException(
                    "LIST-type field '" + req.getFieldKey()
                    + "' requires at least one entry in listOptions");
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
                .orElseThrow(() -> new UdfFieldNotFoundException(
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
            affectedRecordsFinder.ifPresent(finder -> {
                List<UUID> ids = finder.findAffectedIds(orgId, def.getFieldKey());
                LOG.info("Force-deleting UDF field '{}' on org {} — nullifying {} record(s): {}",
                        def.getFieldKey(), orgId, ids.size(), ids);
            });
            valueNullifier.ifPresent(n -> n.nullifyValues(orgId, def.getFieldKey()));
        }

        def.setActive(false);
        repository.save(def);
    }
}
