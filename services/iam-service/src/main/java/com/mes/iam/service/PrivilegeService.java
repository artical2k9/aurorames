package com.mes.iam.service;

import com.mes.iam.api.dto.PrivilegeItemRequest;
import com.mes.iam.domain.Privilege;
import com.mes.iam.domain.Role;
import com.mes.iam.domain.RolePrivilegeAssignment;
import com.mes.iam.repository.PrivilegeRepository;
import com.mes.iam.repository.RolePrivilegeRepository;
import com.mes.iam.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class PrivilegeService {

    private static final String SYSTEM_ADMIN_ROLE = "SYSTEM_ADMIN";
    private static final UUID SYSTEM_ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final PrivilegeRepository privilegeRepository;
    private final RolePrivilegeRepository rolePrivilegeRepository;
    private final RoleRepository roleRepository;

    public PrivilegeService(PrivilegeRepository privilegeRepository,
                            RolePrivilegeRepository rolePrivilegeRepository,
                            RoleRepository roleRepository) {
        this.privilegeRepository = privilegeRepository;
        this.rolePrivilegeRepository = rolePrivilegeRepository;
        this.roleRepository = roleRepository;
    }

    /**
     * Upsert a module's privilege manifest and auto-grant all registered privileges to
     * SYSTEM_ADMIN. Idempotent: re-registering an existing key updates its description
     * and ensures SYSTEM_ADMIN still holds the grant even if it was accidentally revoked.
     */
    public void registerManifest(String moduleName, List<PrivilegeItemRequest> items) {
        List<Role> adminRoles = roleRepository.findByName(SYSTEM_ADMIN_ROLE);
        Role systemAdmin = adminRoles.isEmpty() ? null : adminRoles.get(0);

        Set<String> alreadyGranted = systemAdmin != null
                ? rolePrivilegeRepository.findActiveByRoleId(systemAdmin.getId())
                        .stream()
                        .map(a -> a.getPrivilege().getPrivilegeKey())
                        .collect(Collectors.toSet())
                : Set.of();

        for (PrivilegeItemRequest item : items) {
            Optional<Privilege> existing = privilegeRepository.findByPrivilegeKey(item.privilegeKey());
            Privilege privilege;
            if (existing.isEmpty()) {
                privilege = new Privilege(item.privilegeKey(), moduleName, moduleName);
                privilege.setDescription(item.description());
                privilege = privilegeRepository.save(privilege);
            } else {
                privilege = existing.get();
                privilege.setDescription(item.description());
                privilege = privilegeRepository.save(privilege);
            }

            if (systemAdmin != null && !alreadyGranted.contains(item.privilegeKey())) {
                rolePrivilegeRepository.save(new RolePrivilegeAssignment(systemAdmin, privilege, SYSTEM_ORG_ID));
            }
        }
    }

    @Transactional(readOnly = true)
    public Map<String, List<Privilege>> listByModule() {
        return privilegeRepository.findAll().stream()
                .collect(Collectors.groupingBy(Privilege::getModuleName));
    }

    /**
     * Returns the complete role→privileges map used by lib-common-security for cache
     * warm-up. Returns a union of active assignments across all organisations for each
     * role name (v1 simplification — org-scoped map deferred to a future iteration).
     */
    @Transactional(readOnly = true)
    public Map<String, List<String>> getPrivilegeMap() {
        return rolePrivilegeRepository.findAllActive().stream()
                .collect(Collectors.groupingBy(
                        a -> a.getRole().getName(),
                        Collectors.mapping(
                                a -> a.getPrivilege().getPrivilegeKey(),
                                Collectors.toList())));
    }
}
