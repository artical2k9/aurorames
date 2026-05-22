package com.mikemes.iam.config;

import com.mikemes.common.security.privilege.PrivilegeCache;
import com.mikemes.iam.repository.RolePrivilegeRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * iam-service provides its own PrivilegeCache backed by the local DB, bypassing
 * the HTTP-based CaffeinePrivilegeCache from lib-common-security (which would
 * create a circular self-call). @ConditionalOnMissingBean in the auto-configuration
 * means this bean takes precedence.
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
        return rolePrivilegeRepository.findPrivilegeKeysByRoleName(roleName);
    }
}
