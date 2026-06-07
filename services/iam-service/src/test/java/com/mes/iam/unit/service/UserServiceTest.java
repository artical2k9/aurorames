package com.mes.iam.unit.service;

import com.mes.iam.api.dto.UserResponse;
import com.mes.iam.exception.UserNotFoundException;
import com.mes.iam.keycloak.KeycloakAdminClient;
import com.mes.iam.keycloak.KeycloakTokenClient;
import com.mes.iam.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock KeycloakAdminClient keycloakAdminClient;
    @Mock KeycloakTokenClient keycloakTokenClient;

    @InjectMocks UserService userService;

    static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final String USER_ID = "kc-user-uuid-1";

    @Test
    void createUser_callsCreateAndReturnsResponse() {
        when(keycloakAdminClient.createUser("alice@test.com", "Alice", "Smith", ORG_ID))
                .thenReturn(USER_ID);
        when(keycloakAdminClient.findUserById(USER_ID))
                .thenReturn(Optional.of(userRep(USER_ID, "alice@test.com", "Alice", "Smith", true)));

        UserResponse response = userService.createUser("alice@test.com", "Alice", "Smith",
                ORG_ID, List.of("SYSTEM_ADMIN"), null, false);

        assertThat(response.id()).isEqualTo(USER_ID);
        assertThat(response.email()).isEqualTo("alice@test.com");
        assertThat(response.roles()).containsExactly("SYSTEM_ADMIN");
    }

    @Test
    void createUser_assignsInitialRolesAndSendsPasswordEmail() {
        when(keycloakAdminClient.createUser(anyString(), anyString(), anyString(), eq(ORG_ID)))
                .thenReturn(USER_ID);
        when(keycloakAdminClient.findUserById(USER_ID))
                .thenReturn(Optional.of(userRep(USER_ID, "a@b.com", "A", "B", true)));

        userService.createUser("a@b.com", "A", "B", ORG_ID, List.of("VIEWER"), null, false);

        verify(keycloakAdminClient).assignUserRoles(USER_ID, List.of("VIEWER"));
        verify(keycloakAdminClient).sendPasswordEmail(USER_ID);
    }

    @Test
    void listUsers_delegatesToClientAndMapsResponse() {
        UserRepresentation u1 = userRep("id-1", "one@test.com", "One", "User", true);
        UserRepresentation u2 = userRep("id-2", "two@test.com", "Two", "User", false);
        when(keycloakAdminClient.listUsersByOrgId(ORG_ID)).thenReturn(List.of(u1, u2));
        when(keycloakAdminClient.getUserRoles("id-1")).thenReturn(List.of("SYSTEM_ADMIN"));
        when(keycloakAdminClient.getUserRoles("id-2")).thenReturn(List.of("VIEWER"));

        List<UserResponse> result = userService.listUsers(ORG_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).email()).isEqualTo("one@test.com");
        assertThat(result.get(0).roles()).containsExactly("SYSTEM_ADMIN");
        assertThat(result.get(1).enabled()).isFalse();
    }

    @Test
    void getUser_verifiesOrgAndReturnsResponse() {
        UserRepresentation u = userRepWithOrg(USER_ID, "alice@test.com", ORG_ID);
        when(keycloakAdminClient.findUserById(USER_ID)).thenReturn(Optional.of(u));
        when(keycloakAdminClient.getUserRoles(USER_ID)).thenReturn(List.of("SYSTEM_ADMIN"));

        UserResponse response = userService.getUser(USER_ID, ORG_ID);

        assertThat(response.id()).isEqualTo(USER_ID);
    }

    @Test
    void getUser_notFound_throwsUserNotFoundException() {
        when(keycloakAdminClient.findUserById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser(USER_ID, ORG_ID))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void getUser_differentOrg_throwsUserNotFoundException() {
        UserRepresentation u = userRepWithOrg(USER_ID, "alice@test.com", UUID.randomUUID());
        when(keycloakAdminClient.findUserById(USER_ID)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> userService.getUser(USER_ID, ORG_ID))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void setUserRoles_verifiesOrgAndDelegatesToClient() {
        UserRepresentation u = userRepWithOrg(USER_ID, "alice@test.com", ORG_ID);
        when(keycloakAdminClient.findUserById(USER_ID)).thenReturn(Optional.of(u));

        UserResponse response = userService.setUserRoles(USER_ID, ORG_ID, List.of("VIEWER"));

        verify(keycloakAdminClient).setUserRoles(USER_ID, List.of("VIEWER"));
        assertThat(response.roles()).containsExactly("VIEWER");
    }

    @Test
    void setUserRoles_notFound_throwsUserNotFoundException() {
        when(keycloakAdminClient.findUserById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.setUserRoles(USER_ID, ORG_ID, List.of("SYSTEM_ADMIN")))
                .isInstanceOf(UserNotFoundException.class);

        verify(keycloakAdminClient, never()).setUserRoles(anyString(), anyList());
    }

    @Test
    void deactivateUser_verifiesOrgAndDelegatesToClient() {
        UserRepresentation u = userRepWithOrg(USER_ID, "alice@test.com", ORG_ID);
        when(keycloakAdminClient.findUserById(USER_ID)).thenReturn(Optional.of(u));

        userService.deactivateUser(USER_ID, ORG_ID);

        verify(keycloakAdminClient).deactivateUser(USER_ID);
    }

    @Test
    void deactivateUser_notFound_throwsUserNotFoundException() {
        when(keycloakAdminClient.findUserById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deactivateUser(USER_ID, ORG_ID))
                .isInstanceOf(UserNotFoundException.class);

        verify(keycloakAdminClient, never()).deactivateUser(anyString());
    }

    @Test
    void deactivateUser_differentOrg_throwsUserNotFoundException() {
        UserRepresentation u = userRepWithOrg(USER_ID, "alice@test.com", UUID.randomUUID());
        when(keycloakAdminClient.findUserById(USER_ID)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> userService.deactivateUser(USER_ID, ORG_ID))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    static UserRepresentation userRep(String id, String email, String first, String last,
                                       boolean enabled) {
        UserRepresentation u = new UserRepresentation();
        u.setId(id);
        u.setEmail(email);
        u.setFirstName(first);
        u.setLastName(last);
        u.setEnabled(enabled);
        return u;
    }

    static UserRepresentation userRepWithOrg(String id, String email, UUID orgId) {
        UserRepresentation u = userRep(id, email, "First", "Last", true);
        u.setAttributes(Map.of("org_id", List.of(orgId.toString())));
        return u;
    }
}
