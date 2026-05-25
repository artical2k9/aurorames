package com.mes.platform.service;

import com.mes.platform.api.dto.SystemConfigDto;
import com.mes.platform.api.dto.UpsertConfigRequest;
import com.mes.platform.domain.SystemConfiguration;
import com.mes.platform.repository.SystemConfigurationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class SystemConfigService {

    private final SystemConfigurationRepository repository;

    public SystemConfigService(SystemConfigurationRepository repository) {
        this.repository = repository;
    }

    public SystemConfigDto upsert(UUID orgId, String key, UpsertConfigRequest req, String createdBy) {
        SystemConfiguration entity = repository
                .findByOrgIdAndConfigKeyAndActiveTrue(orgId, key)
                .orElseGet(SystemConfiguration::new);

        if (entity.getId() == null) {
            entity.setOrgId(orgId);
            entity.setConfigKey(key);
            entity.setCreatedBy(createdBy);
        }
        entity.setConfigValue(req.value());
        entity.setDescription(req.description());
        entity.setActive(true);

        return SystemConfigDto.from(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public Optional<SystemConfigDto> findByKey(UUID orgId, String key) {
        return repository.findByOrgIdAndConfigKeyAndActiveTrue(orgId, key)
                .map(SystemConfigDto::from);
    }

    @Transactional(readOnly = true)
    public List<SystemConfigDto> listAll(UUID orgId) {
        return repository.findByOrgIdAndActiveTrue(orgId).stream()
                .map(SystemConfigDto::from)
                .toList();
    }

    public void softDelete(UUID orgId, String key) {
        SystemConfiguration entity = repository
                .findByOrgIdAndConfigKeyAndActiveTrue(orgId, key)
                .orElseThrow(() -> new NoSuchElementException("Config key not found: " + key));
        entity.setActive(false);
        repository.save(entity);
    }
}
