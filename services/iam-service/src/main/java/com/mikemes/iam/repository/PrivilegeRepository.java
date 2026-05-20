package com.mikemes.iam.repository;

import com.mikemes.iam.domain.Privilege;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PrivilegeRepository extends JpaRepository<Privilege, UUID> {

    Optional<Privilege> findByPrivilegeKey(String privilegeKey);
}
