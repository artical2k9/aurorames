package com.mes.iam.service;

import com.mes.iam.api.dto.PrivilegeItemRequest;
import com.mes.iam.domain.Privilege;
import com.mes.iam.repository.PrivilegeRepository;
import com.mes.iam.repository.RolePrivilegeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class PrivilegeService {

    private final PrivilegeRepository privilegeRepository;
    private final RolePrivilegeRepository rolePrivilegeRepository;

    public PrivilegeService(PrivilegeRepository privilegeRepository,
                            RolePrivilegeRepository rolePrivilegeRepository) {
        this.privilegeRepository = privilegeRepository;
        this.rolePrivilegeRepository = rolePrivilegeRepository;
    }

    /**
     * Upsert a module's privilege manifest. Idempotent: calling with the same
     * privilegeKey twice produces exactly one DB row; description is updated if changed.
     */
    public void registerManifest(String moduleName, List<PrivilegeItemRequest> items) {
        for (PrivilegeItemRequest item : items) {
            Optional<Privilege> existing = privilegeRepository.findByPrivilegeKey(item.privilegeKey());
            if (existing.isEmpty()) {
                Privilege privilege = new Privilege(item.privilegeKey(), moduleName, moduleName);
                privilege.setDescription(item.description());
                privilegeRepository.save(privilege);
            } else {
                Privilege privilege = existing.get();
                privilege.setDescription(item.description());
                privilegeRepository.save(privilege);
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
