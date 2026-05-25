package com.mes.iam.service;

import com.mes.iam.domain.Privilege;
import com.mes.iam.domain.Role;
import com.mes.iam.domain.RolePrivilegeAssignment;
import com.mes.iam.exception.ActiveRoleAssignmentsException;
import com.mes.iam.exception.DuplicateRoleNameException;
import com.mes.iam.exception.PrivilegeNotFoundException;
import com.mes.iam.exception.RoleNotFoundException;
import com.mes.iam.exception.SystemRoleException;
import com.mes.iam.kafka.PrivilegeChangeEventPublisher;
import com.mes.iam.keycloak.KeycloakAdminClient;
import com.mes.iam.repository.PrivilegeRepository;
import com.mes.iam.repository.RolePrivilegeRepository;
import com.mes.iam.repository.RoleRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class RoleService {

    private final RoleRepository roleRepository;
    private final PrivilegeRepository privilegeRepository;
    private final RolePrivilegeRepository rolePrivilegeRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final PrivilegeChangeEventPublisher eventPublisher;

    public RoleService(RoleRepository roleRepository,
                       PrivilegeRepository privilegeRepository,
                       RolePrivilegeRepository rolePrivilegeRepository,
                       KeycloakAdminClient keycloakAdminClient,
                       PrivilegeChangeEventPublisher eventPublisher) {
        this.roleRepository = roleRepository;
        this.privilegeRepository = privilegeRepository;
        this.rolePrivilegeRepository = rolePrivilegeRepository;
        this.keycloakAdminClient = keycloakAdminClient;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<Role> listRoles(UUID orgId) {
        return roleRepository.findByOrgId(orgId);
    }

    @Transactional(readOnly = true)
    public long countActivePrivileges(UUID roleId) {
        return rolePrivilegeRepository.countActiveByRoleId(roleId);
    }

    /**
     * Creates a role in DB first, then mirrors to Keycloak. If the Keycloak call
     * fails, @Transactional rolls back the DB insert automatically (R-07 pattern).
     */
    public Role createRole(UUID orgId, String name, String description) {
        if (roleRepository.findByOrgIdAndName(orgId, name).isPresent()) {
            throw new DuplicateRoleNameException(name);
        }

        Role role = new Role(orgId, name);
        if (description != null) {
            role.setDescription(description);
        }
        role = roleRepository.save(role);

        String keycloakRoleId = keycloakAdminClient.createRole(name);
        role.setKeycloakRoleId(keycloakRoleId);
        return roleRepository.save(role);
    }

    public void deleteRole(UUID roleId, UUID orgId) {
        Role role = roleRepository.findById(roleId)
                .filter(r -> r.getOrgId().equals(orgId))
                .orElseThrow(() -> new RoleNotFoundException(roleId));

        if (role.isSystemRole()) {
            throw new SystemRoleException("System roles cannot be deleted");
        }

        int userCount = keycloakAdminClient.countUsersWithRole(role.getName());
        if (userCount > 0) {
            throw new ActiveRoleAssignmentsException(userCount);
        }

        keycloakAdminClient.deleteRole(role.getName());
        roleRepository.delete(role);
    }

    @Transactional(readOnly = true)
    public List<RolePrivilegeAssignment> getRolePrivileges(UUID roleId, UUID orgId) {
        roleRepository.findById(roleId)
                .filter(r -> r.getOrgId().equals(orgId))
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        return rolePrivilegeRepository.findActiveByRoleId(roleId);
    }

    public void grantPrivilege(UUID roleId, UUID privilegeId, UUID orgId) {
        Role role = roleRepository.findById(roleId)
                .filter(r -> r.getOrgId().equals(orgId))
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        Privilege privilege = privilegeRepository.findById(privilegeId)
                .orElseThrow(() -> new PrivilegeNotFoundException(privilegeId));

        boolean alreadyActive = rolePrivilegeRepository.findActiveByRoleId(roleId).stream()
                .anyMatch(a -> privilege.getPrivilegeKey().equals(a.getPrivilege().getPrivilegeKey()));
        if (alreadyActive) {
            return;
        }

        rolePrivilegeRepository.save(new RolePrivilegeAssignment(role, privilege, orgId));
        eventPublisher.publishGrant(role.getName(), privilege.getPrivilegeKey(), orgId);
    }

    public void revokePrivilege(UUID roleId, UUID privilegeId, UUID orgId) {
        Role role = roleRepository.findById(roleId)
                .filter(r -> r.getOrgId().equals(orgId))
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        Privilege privilege = privilegeRepository.findById(privilegeId)
                .orElseThrow(() -> new PrivilegeNotFoundException(privilegeId));

        RolePrivilegeAssignment assignment = rolePrivilegeRepository.findActiveByRoleId(roleId).stream()
                .filter(a -> privilege.getPrivilegeKey().equals(a.getPrivilege().getPrivilegeKey()))
                .findFirst()
                .orElseThrow(() -> new RoleNotFoundException(roleId));

        assignment.revoke(currentUsername());
        rolePrivilegeRepository.save(assignment);
        eventPublisher.publishRevoke(role.getName(), privilege.getPrivilegeKey(), orgId);
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        return "system";
    }
}
