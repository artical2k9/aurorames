package com.mikemes.iam.repository;

import com.mikemes.iam.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    List<Role> findByOrgId(UUID orgId);
}
