package com.mes.workorder.preferences.repository;

import com.mes.workorder.preferences.domain.UserGridPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserGridPreferenceRepository extends JpaRepository<UserGridPreference, UUID> {

    Optional<UserGridPreference> findByOrgIdAndUserIdAndModuleKey(UUID orgId, String userId, String moduleKey);
}
