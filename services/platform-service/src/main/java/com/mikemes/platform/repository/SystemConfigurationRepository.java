package com.mikemes.platform.repository;

import com.mikemes.platform.domain.SystemConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SystemConfigurationRepository extends JpaRepository<SystemConfiguration, Long> {

    Optional<SystemConfiguration> findByOrgIdAndConfigKeyAndActiveTrue(UUID orgId, String configKey);

    List<SystemConfiguration> findByOrgIdAndActiveTrue(UUID orgId);
}
