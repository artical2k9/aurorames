package com.mikemes.iam.unit.service;

import com.mikemes.iam.domain.Privilege;
import com.mikemes.iam.domain.Role;
import com.mikemes.iam.domain.RolePrivilegeAssignment;
import com.mikemes.iam.exception.ActiveRoleAssignmentsException;
import com.mikemes.iam.exception.DuplicateRoleNameException;
import com.mikemes.iam.exception.RoleNotFoundException;
import com.mikemes.iam.exception.SystemRoleException;
import com.mikemes.iam.kafka.PrivilegeChangeEventPublisher;
import com.mikemes.iam.keycloak.KeycloakAdminClient;
import com.mikemes.iam.repository.PrivilegeRepository;
import com.mikemes.iam.repository.RolePrivilegeRepository;
import com.mikemes.iam.repository.RoleRepository;
import com.mikemes.iam.service.RoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock RoleRepository roleRepository;
    @Mock PrivilegeRepository privilegeRepository;
    @Mock RolePrivilegeRepository rolePrivilegeRepository;
    @Mock KeycloakAdminClient keycloakAdminClient;
    @Mock PrivilegeChangeEventPublisher eventPublisher;

    @InjectMocks RoleService roleService;

    static final UUID ORG_ID = UUID.randomUUID();
    static final UUID ROLE_ID = UUID.randomUUID();
    static final UUID PRIV_ID = UUID.randomUUID();

    Role customRole;
    Role systemRole;

    @BeforeEach
    void setUp() {
        customRole = new Role(ORG_ID, "SENIOR_INSPECTOR");
        systemRole = new Role(ORG_ID, "ADMIN");
        systemRole.setSystemRole(true);
    }

    @Test
    void createRole_persistsAndMirrorsToKeycloak() {
        when(roleRepository.findByOrgIdAndName(ORG_ID, "SENIOR_INSPECTOR")).thenReturn(Optional.empty());
        when(roleRepository.save(any())).thenReturn(customRole);
        when(keycloakAdminClient.createRole("SENIOR_INSPECTOR")).thenReturn("kc-id-123");

        Role result = roleService.createRole(ORG_ID, "SENIOR_INSPECTOR", "Senior inspector role");

        assertThat(result).isNotNull();
        verify(keycloakAdminClient).createRole("SENIOR_INSPECTOR");
    }

    @Test
    void createRole_duplicateName_throwsDuplicateRoleNameException() {
        when(roleRepository.findByOrgIdAndName(ORG_ID, "VIEWER")).thenReturn(Optional.of(customRole));

        assertThatThrownBy(() -> roleService.createRole(ORG_ID, "VIEWER", null))
                .isInstanceOf(DuplicateRoleNameException.class)
                .hasMessageContaining("VIEWER");

        verify(keycloakAdminClient, never()).createRole(any());
    }

    @Test
    void createRole_keycloakFails_propagatesException() {
        when(roleRepository.findByOrgIdAndName(ORG_ID, "FAIL_ROLE")).thenReturn(Optional.empty());
        when(roleRepository.save(any())).thenReturn(customRole);
        when(keycloakAdminClient.createRole("FAIL_ROLE"))
                .thenThrow(new RuntimeException("Keycloak unavailable"));

        assertThatThrownBy(() -> roleService.createRole(ORG_ID, "FAIL_ROLE", null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Keycloak unavailable");
    }

    @Test
    void deleteRole_systemRole_throwsSystemRoleException() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(systemRole));

        assertThatThrownBy(() -> roleService.deleteRole(ROLE_ID, ORG_ID))
                .isInstanceOf(SystemRoleException.class)
                .hasMessageContaining("System roles cannot be deleted");

        verify(keycloakAdminClient, never()).deleteRole(any());
    }

    @Test
    void deleteRole_hasActiveUsers_throwsActiveRoleAssignmentsException() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(customRole));
        when(keycloakAdminClient.countUsersWithRole("SENIOR_INSPECTOR")).thenReturn(3);

        assertThatThrownBy(() -> roleService.deleteRole(ROLE_ID, ORG_ID))
                .isInstanceOf(ActiveRoleAssignmentsException.class)
                .hasMessageContaining("3");

        verify(keycloakAdminClient, never()).deleteRole(any());
    }

    @Test
    void deleteRole_noActiveUsers_deletesFromKeycloakAndDb() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(customRole));
        when(keycloakAdminClient.countUsersWithRole("SENIOR_INSPECTOR")).thenReturn(0);

        roleService.deleteRole(ROLE_ID, ORG_ID);

        verify(keycloakAdminClient).deleteRole("SENIOR_INSPECTOR");
        verify(roleRepository).delete(customRole);
    }

    @Test
    void deleteRole_roleNotFound_throwsRoleNotFoundException() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.deleteRole(ROLE_ID, ORG_ID))
                .isInstanceOf(RoleNotFoundException.class);
    }

    @Test
    void grantPrivilege_createsAssignmentAndPublishesEvent() {
        Privilege privilege = buildPrivilege();
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(customRole));
        when(privilegeRepository.findById(PRIV_ID)).thenReturn(Optional.of(privilege));
        when(rolePrivilegeRepository.findActiveByRoleId(ROLE_ID)).thenReturn(List.of());
        when(rolePrivilegeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        roleService.grantPrivilege(ROLE_ID, PRIV_ID, ORG_ID);

        verify(rolePrivilegeRepository).save(any(RolePrivilegeAssignment.class));
        verify(eventPublisher).publishGrant("SENIOR_INSPECTOR", "iam:roles:manage", ORG_ID);
    }

    @Test
    void grantPrivilege_alreadyGranted_isIdempotent() {
        Privilege privilege = buildPrivilege();
        RolePrivilegeAssignment existing = new RolePrivilegeAssignment(customRole, privilege, ORG_ID);
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(customRole));
        when(privilegeRepository.findById(PRIV_ID)).thenReturn(Optional.of(privilege));
        when(rolePrivilegeRepository.findActiveByRoleId(ROLE_ID)).thenReturn(List.of(existing));

        roleService.grantPrivilege(ROLE_ID, PRIV_ID, ORG_ID);

        verify(rolePrivilegeRepository, never()).save(any());
        verify(eventPublisher, never()).publishGrant(any(), any(), any());
    }

    @Test
    void revokePrivilege_setsRevokedAtAndPublishesEvent() {
        Privilege privilege = buildPrivilege();
        RolePrivilegeAssignment assignment = new RolePrivilegeAssignment(customRole, privilege, ORG_ID);
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(customRole));
        when(privilegeRepository.findById(PRIV_ID)).thenReturn(Optional.of(privilege));
        when(rolePrivilegeRepository.findActiveByRoleId(ROLE_ID)).thenReturn(List.of(assignment));
        when(rolePrivilegeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        roleService.revokePrivilege(ROLE_ID, PRIV_ID, ORG_ID);

        assertThat(assignment.getRevokedAt()).isNotNull();
        verify(eventPublisher).publishRevoke("SENIOR_INSPECTOR", "iam:roles:manage", ORG_ID);
    }

    private Privilege buildPrivilege() {
        Privilege p = new Privilege("iam:roles:manage", "iam", "iam-service");
        return p;
    }
}
