package com.mes.iam.repository;

import com.mes.iam.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    List<Role> findByOrgId(UUID orgId);

    List<Role> findByName(String name);

    Optional<Role> findByOrgIdAndName(UUID orgId, String name);
}
