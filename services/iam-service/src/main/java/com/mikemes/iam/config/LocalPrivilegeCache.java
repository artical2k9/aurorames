package com.mikemes.iam.config;

import com.mikemes.common.security.privilege.PrivilegeCache;
import com.mikemes.iam.repository.RolePrivilegeRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * iam-service provides its own PrivilegeCache backed by the local DB, bypassing
 * the HTTP-based CaffeinePrivilegeCache from lib-common-security (which would
 * create a circular self-call). @ConditionalOnMissingBean in the auto-configuration
 * means this bean takes precedence.
 *
 * Uses findAllActive() + in-memory filter rather than a parameterised JPQL query
 * because Hibernate 6.5 returns 0 rows for any WHERE clause that navigates through
 * a lazy @ManyToOne association on RolePrivilegeAssignment — both implicit
 * (a.role.id = ?) and explicit JOIN (JOIN a.role r WHERE r.name = ?). findAllActive()
 * has no association navigation in its WHERE clause and is unaffected by this bug.
 */
@Component
public class LocalPrivilegeCache implements PrivilegeCache {

    private final RolePrivilegeRepository rolePrivilegeRepository;

    public LocalPrivilegeCache(RolePrivilegeRepository rolePrivilegeRepository) {
        this.rolePrivilegeRepository = rolePrivilegeRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> getPrivilegesForRole(String roleName) {
        return rolePrivilegeRepository.findAllActive().stream()
                .filter(a -> roleName.equals(a.getRole().getName()))
                .map(a -> a.getPrivilege().getPrivilegeKey())
                .collect(Collectors.toSet());
    }
}
