package com.mes.workorder.preferences.service;

import com.mes.workorder.preferences.api.dto.ColumnPreferenceEntry;
import com.mes.workorder.preferences.api.dto.UserGridPreferenceDto;
import com.mes.workorder.preferences.domain.UserGridPreference;
import com.mes.workorder.preferences.repository.UserGridPreferenceRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class UserGridPreferenceService {

    private static final Logger LOG = LoggerFactory.getLogger(UserGridPreferenceService.class);
    private static final TypeReference<List<ColumnPreferenceEntry>> COLUMN_TYPE = new TypeReference<>() {};

    private final UserGridPreferenceRepository repository;
    private final Map<String, List<ColumnPreferenceEntry>> defaultColumnRegistry;
    private final ObjectMapper objectMapper;

    public UserGridPreferenceService(
            UserGridPreferenceRepository repository,
            Map<String, List<ColumnPreferenceEntry>> defaultColumnRegistry,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.defaultColumnRegistry = defaultColumnRegistry;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public UserGridPreferenceDto get(UUID orgId, String userId, String moduleKey) {
        return repository.findByOrgIdAndUserIdAndModuleKey(orgId, userId, moduleKey)
                .map(pref -> {
                    List<ColumnPreferenceEntry> cols;
                    try {
                        cols = objectMapper.convertValue(pref.getColumnConfig(), COLUMN_TYPE);
                    } catch (IllegalArgumentException e) {
                        LOG.warn("Corrupted column_config for org={} user={} module={}, returning defaults: {}",
                                orgId, userId, moduleKey, e.getMessage());
                        cols = defaultColumnRegistry.getOrDefault(moduleKey, List.of());
                    }
                    return new UserGridPreferenceDto(moduleKey, cols);
                })
                .orElseGet(() -> {
                    List<ColumnPreferenceEntry> defaults = defaultColumnRegistry.getOrDefault(moduleKey, List.of());
                    return new UserGridPreferenceDto(moduleKey, defaults);
                });
    }

    public UserGridPreferenceDto upsert(UUID orgId, String userId, String moduleKey,
                                        List<ColumnPreferenceEntry> columns) {
        UserGridPreference pref = repository
                .findByOrgIdAndUserIdAndModuleKey(orgId, userId, moduleKey)
                .orElseGet(() -> {
                    UserGridPreference p = new UserGridPreference();
                    p.setOrgId(orgId);
                    p.setUserId(userId);
                    p.setModuleKey(moduleKey);
                    return p;
                });

        List<Map<String, Object>> config = objectMapper.convertValue(columns, new TypeReference<>() {});
        pref.setColumnConfig(config);
        pref.setUpdatedAt(Instant.now());
        repository.save(pref);

        return new UserGridPreferenceDto(moduleKey, columns);
    }
}
