package com.mikemes.iam.config;

import com.mikemes.common.security.privilege.PrivilegeCache;
import com.mikemes.iam.domain.Role;
import com.mikemes.iam.repository.RolePrivilegeRepository;
import com.mikemes.iam.repository.RoleRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * iam-service provides its own PrivilegeCache backed by the local DB, bypassing
 * the HTTP-based CaffeinePrivilegeCache from lib-common-security (which would
 * create a circular self-call). @ConditionalOnMissingBean in the auto-configuration
 * means this bean takes precedence.
 */
@Component
public class LocalPrivilegeCache implements PrivilegeCache {

    private final RoleRepository roleRepository;
    private final RolePrivilegeRepository rolePrivilegeRepository;

    public LocalPrivilegeCache(RoleRepository roleRepository,
                               RolePrivilegeRepository rolePrivilegeRepository) {
        this.roleRepository = roleRepository;
        this.rolePrivilegeRepository = rolePrivilegeRepository;
    }

    @Override
    public Set<String> getPrivilegesForRole(String roleName) {
        List<Role> roles = roleRepository.findByName(roleName);
        return roles.stream()
                .flatMap(role -> rolePrivilegeRepository.findActiveByRoleId(role.getId()).stream())
                .map(assignment -> assignment.getPrivilege().getPrivilegeKey())
                .collect(Collectors.toSet());
    }
}
