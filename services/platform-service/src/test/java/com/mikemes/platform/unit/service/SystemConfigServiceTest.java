package com.mikemes.platform.unit.service;

import com.mikemes.platform.api.dto.UpsertConfigRequest;
import com.mikemes.platform.domain.SystemConfiguration;
import com.mikemes.platform.repository.SystemConfigurationRepository;
import com.mikemes.platform.service.SystemConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemConfigServiceTest {

    static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final String CONFIG_KEY = "test.key";

    @Mock
    SystemConfigurationRepository repository;

    @InjectMocks
    SystemConfigService service;

    @Test
    void upsert_creates_new_entry_when_absent() {
        when(repository.findByOrgIdAndConfigKeyAndActiveTrue(ORG_ID, CONFIG_KEY))
                .thenReturn(Optional.empty());

        ArgumentCaptor<SystemConfiguration> captor = ArgumentCaptor.forClass(SystemConfiguration.class);
        SystemConfiguration saved = new SystemConfiguration();
        saved.setOrgId(ORG_ID);
        saved.setConfigKey(CONFIG_KEY);
        saved.setConfigValue("new-value");
        saved.setActive(true);
        when(repository.save(any())).thenReturn(saved);

        service.upsert(ORG_ID, CONFIG_KEY, new UpsertConfigRequest("new-value", "desc"), "test-user");

        verify(repository).save(captor.capture());
        SystemConfiguration entity = captor.getValue();
        assertThat(entity.getOrgId()).isEqualTo(ORG_ID);
        assertThat(entity.getConfigKey()).isEqualTo(CONFIG_KEY);
        assertThat(entity.getConfigValue()).isEqualTo("new-value");
        assertThat(entity.isActive()).isTrue();
    }

    @Test
    void upsert_updates_existing_entry_when_present() {
        SystemConfiguration existing = new SystemConfiguration();
        existing.setOrgId(ORG_ID);
        existing.setConfigKey(CONFIG_KEY);
        existing.setConfigValue("old-value");
        existing.setActive(true);
        when(repository.findByOrgIdAndConfigKeyAndActiveTrue(ORG_ID, CONFIG_KEY))
                .thenReturn(Optional.of(existing));
        when(repository.save(any())).thenReturn(existing);

        service.upsert(ORG_ID, CONFIG_KEY, new UpsertConfigRequest("updated-value", null), "test-user");

        ArgumentCaptor<SystemConfiguration> captor = ArgumentCaptor.forClass(SystemConfiguration.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getConfigValue()).isEqualTo("updated-value");
    }

    @Test
    void softDelete_sets_active_false() {
        SystemConfiguration existing = new SystemConfiguration();
        existing.setOrgId(ORG_ID);
        existing.setConfigKey(CONFIG_KEY);
        existing.setConfigValue("val");
        existing.setActive(true);
        when(repository.findByOrgIdAndConfigKeyAndActiveTrue(ORG_ID, CONFIG_KEY))
                .thenReturn(Optional.of(existing));
        when(repository.save(any())).thenReturn(existing);

        service.softDelete(ORG_ID, CONFIG_KEY);

        ArgumentCaptor<SystemConfiguration> captor = ArgumentCaptor.forClass(SystemConfiguration.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
    }

    @Test
    void findByKey_returns_empty_for_inactive_entry() {
        when(repository.findByOrgIdAndConfigKeyAndActiveTrue(ORG_ID, CONFIG_KEY))
                .thenReturn(Optional.empty());

        assertThat(service.findByKey(ORG_ID, CONFIG_KEY)).isEmpty();
    }
}
