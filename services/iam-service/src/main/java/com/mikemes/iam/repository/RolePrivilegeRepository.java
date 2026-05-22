package com.mikemes.iam.repository;

import com.mikemes.iam.domain.RolePrivilegeAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface RolePrivilegeRepository extends JpaRepository<RolePrivilegeAssignment, UUID> {

    @Query("SELECT a FROM RolePrivilegeAssignment a JOIN FETCH a.privilege "
            + "WHERE a.role.id = :roleId AND a.revokedAt IS NULL")
    List<RolePrivilegeAssignment> findActiveByRoleId(@Param("roleId") UUID roleId);

    // Used by LocalPrivilegeCache: explicit JOINs to avoid implicit lazy-association
    // navigation on a.role.id, which in Hibernate 6.5 fails to return results.
    @Query("SELECT p.privilegeKey FROM RolePrivilegeAssignment a "
            + "JOIN a.role r JOIN a.privilege p "
            + "WHERE r.name = :roleName AND a.revokedAt IS NULL")
    Set<String> findPrivilegeKeysByRoleName(@Param("roleName") String roleName);

    @Query("SELECT a FROM RolePrivilegeAssignment a WHERE a.orgId = :orgId AND a.revokedAt IS NULL")
    List<RolePrivilegeAssignment> findActiveByOrgId(@Param("orgId") UUID orgId);

    @Query("SELECT COUNT(a) FROM RolePrivilegeAssignment a WHERE a.role.id = :roleId AND a.revokedAt IS NULL")
    long countActiveByRoleId(@Param("roleId") UUID roleId);

    @Query("SELECT a FROM RolePrivilegeAssignment a WHERE a.revokedAt IS NULL")
    List<RolePrivilegeAssignment> findAllActive();
}
