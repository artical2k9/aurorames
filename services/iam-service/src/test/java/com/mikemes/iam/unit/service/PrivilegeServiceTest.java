package com.mikemes.iam.unit.service;

import com.mikemes.iam.api.dto.PrivilegeItemRequest;
import com.mikemes.iam.domain.Privilege;
import com.mikemes.iam.domain.Role;
import com.mikemes.iam.domain.RolePrivilegeAssignment;
import com.mikemes.iam.repository.PrivilegeRepository;
import com.mikemes.iam.repository.RolePrivilegeRepository;
import com.mikemes.iam.service.PrivilegeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrivilegeServiceTest {

    @Mock PrivilegeRepository privilegeRepository;
    @Mock RolePrivilegeRepository rolePrivilegeRepository;

    @InjectMocks PrivilegeService privilegeService;

    static final UUID ORG_ID = UUID.randomUUID();

    @Test
    void registerManifest_newPrivilege_savesIt() {
        when(privilegeRepository.findByPrivilegeKey("quality:inspection:view"))
                .thenReturn(Optional.empty());
        when(privilegeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        privilegeService.registerManifest("quality", List.of(
                new PrivilegeItemRequest("quality:inspection:view", "View inspections")));

        verify(privilegeRepository).save(any(Privilege.class));
    }

    @Test
    void registerManifest_existingPrivilege_updatesDescriptionWithoutDuplicate() {
        Privilege existing = new Privilege("quality:inspection:view", "quality", "quality");
        when(privilegeRepository.findByPrivilegeKey("quality:inspection:view"))
                .thenReturn(Optional.of(existing));
        when(privilegeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        privilegeService.registerManifest("quality", List.of(
                new PrivilegeItemRequest("quality:inspection:view", "Updated description")));

        verify(privilegeRepository, times(1)).save(existing);
        assertThat(existing.getDescription()).isEqualTo("Updated description");
    }

    @Test
    void registerManifest_calledTwice_samePriority_onlyOneRow() {
        when(privilegeRepository.findByPrivilegeKey("quality:inspection:view"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new Privilege("quality:inspection:view", "quality", "quality")));
        when(privilegeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PrivilegeItemRequest item = new PrivilegeItemRequest("quality:inspection:view", "View");
        privilegeService.registerManifest("quality", List.of(item));
        privilegeService.registerManifest("quality", List.of(item));

        verify(privilegeRepository, times(2)).save(any());
    }

    @Test
    void listByModule_groupsPrivilegesByModuleName() {
        Privilege p1 = new Privilege("iam:users:view", "iam", "iam-service");
        Privilege p2 = new Privilege("iam:roles:manage", "iam", "iam-service");
        Privilege p3 = new Privilege("quality:inspection:view", "quality", "quality-service");
        when(privilegeRepository.findAll()).thenReturn(List.of(p1, p2, p3));

        Map<String, List<Privilege>> result = privilegeService.listByModule();

        assertThat(result).containsKey("iam");
        assertThat(result).containsKey("quality");
        assertThat(result.get("iam")).hasSize(2);
        assertThat(result.get("quality")).hasSize(1);
    }

    @Test
    void getPrivilegeMap_returnsRoleToPrivilegesMap() {
        Role adminRole = new Role(ORG_ID, "ADMIN");
        Role viewerRole = new Role(ORG_ID, "VIEWER");
        Privilege p1 = new Privilege("iam:roles:manage", "iam", "iam-service");
        Privilege p2 = new Privilege("iam:users:view", "iam", "iam-service");

        RolePrivilegeAssignment a1 = new RolePrivilegeAssignment(adminRole, p1, ORG_ID);
        RolePrivilegeAssignment a2 = new RolePrivilegeAssignment(adminRole, p2, ORG_ID);
        RolePrivilegeAssignment a3 = new RolePrivilegeAssignment(viewerRole, p2, ORG_ID);

        when(rolePrivilegeRepository.findAllActive()).thenReturn(List.of(a1, a2, a3));

        Map<String, List<String>> map = privilegeService.getPrivilegeMap();

        assertThat(map).containsKey("ADMIN");
        assertThat(map).containsKey("VIEWER");
        assertThat(map.get("ADMIN")).containsExactlyInAnyOrder("iam:roles:manage", "iam:users:view");
        assertThat(map.get("VIEWER")).containsExactly("iam:users:view");
    }

    @Test
    void getPrivilegeMap_noAssignments_returnsEmptyMap() {
        when(rolePrivilegeRepository.findAllActive()).thenReturn(List.of());

        Map<String, List<String>> map = privilegeService.getPrivilegeMap();

        assertThat(map).isEmpty();
    }

    @Test
    void registerManifest_multipleItems_savesAll() {
        when(privilegeRepository.findByPrivilegeKey(any())).thenReturn(Optional.empty());
        when(privilegeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        privilegeService.registerManifest("quality", List.of(
                new PrivilegeItemRequest("quality:inspection:view", "View"),
                new PrivilegeItemRequest("quality:inspection:sign-off", "Sign off")));

        verify(privilegeRepository, times(2)).save(any(Privilege.class));
    }

    @Test
    void listByModule_emptyDb_returnsEmptyMap() {
        when(privilegeRepository.findAll()).thenReturn(List.of());

        Map<String, List<Privilege>> result = privilegeService.listByModule();

        assertThat(result).isEmpty();
        verify(rolePrivilegeRepository, never()).findAllActive();
    }
}
