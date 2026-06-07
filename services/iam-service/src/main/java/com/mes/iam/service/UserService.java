package com.mes.iam.service;

import com.mes.iam.api.dto.UserResponse;
import com.mes.iam.exception.InvalidCredentialsException;
import com.mes.iam.exception.UserNotFoundException;
import com.mes.iam.keycloak.KeycloakAdminClient;
import com.mes.iam.keycloak.KeycloakTokenClient;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final KeycloakAdminClient keycloakAdminClient;
    private final KeycloakTokenClient keycloakTokenClient;

    public UserService(KeycloakAdminClient keycloakAdminClient,
                       KeycloakTokenClient keycloakTokenClient) {
        this.keycloakAdminClient = keycloakAdminClient;
        this.keycloakTokenClient = keycloakTokenClient;
    }

    public UserResponse createUser(String email, String firstName, String lastName,
                                    UUID orgId, List<String> roles,
                                    String initialPassword, boolean temporaryPassword) {
        String userId = keycloakAdminClient.createUser(email, firstName, lastName, orgId);
        keycloakAdminClient.assignUserRoles(userId, roles);
        if (initialPassword != null && !initialPassword.isBlank()) {
            keycloakAdminClient.setPassword(userId, initialPassword, temporaryPassword);
        } else {
            keycloakAdminClient.sendPasswordEmail(userId);
        }
        UserRepresentation u = keycloakAdminClient.findUserById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return toResponse(u, roles);
    }

    public List<UserResponse> listUsers(UUID orgId) {
        return keycloakAdminClient.listUsersByOrgId(orgId).stream()
                .map(u -> toResponse(u, keycloakAdminClient.getUserRoles(u.getId())))
                .toList();
    }

    public UserResponse getUser(String userId, UUID orgId) {
        UserRepresentation u = keycloakAdminClient.findUserById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        verifyOrgOwnership(u, orgId);
        return toResponse(u, keycloakAdminClient.getUserRoles(userId));
    }

    public UserResponse setUserRoles(String userId, UUID callerOrgId, List<String> roleNames) {
        UserRepresentation u = keycloakAdminClient.findUserById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        verifyOrgOwnership(u, callerOrgId);
        keycloakAdminClient.setUserRoles(userId, roleNames);
        return toResponse(u, roleNames);
    }

    public void deactivateUser(String userId, UUID callerOrgId) {
        UserRepresentation u = keycloakAdminClient.findUserById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        verifyOrgOwnership(u, callerOrgId);
        keycloakAdminClient.deactivateUser(userId);
    }

    public void resetUserPassword(String userId, UUID callerOrgId,
                                   String newPassword, boolean temporary) {
        UserRepresentation u = keycloakAdminClient.findUserById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        verifyOrgOwnership(u, callerOrgId);
        keycloakAdminClient.setPassword(userId, newPassword, temporary);
        if (!temporary) {
            keycloakAdminClient.clearRequiredActions(userId);
        }
    }

    public void changeTemporaryPassword(String username, String currentPassword,
                                         String newPassword) {
        // Verify current password — throws InvalidCredentialsException on failure
        keycloakTokenClient.verifyTemporaryPasswordCredentials(username, currentPassword);

        // Find the user by email (KC username = email)
        UserRepresentation u = keycloakAdminClient.findUserByEmail(username)
                .orElseThrow(InvalidCredentialsException::new);

        keycloakAdminClient.setPassword(u.getId(), newPassword, false);
        keycloakAdminClient.clearRequiredActions(u.getId());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void verifyOrgOwnership(UserRepresentation u, UUID callerOrgId) {
        String userOrgId = Optional.ofNullable(u.getAttributes())
                .map(attrs -> attrs.get("org_id"))
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(0))
                .orElse(null);
        if (!callerOrgId.toString().equals(userOrgId)) {
            throw new UserNotFoundException(u.getId());
        }
    }

    private static UserResponse toResponse(UserRepresentation u, List<String> roles) {
        boolean passwordChangeRequired = u.getRequiredActions() != null
                && u.getRequiredActions().contains("UPDATE_PASSWORD");
        return new UserResponse(
                u.getId(),
                u.getEmail(),
                u.getFirstName(),
                u.getLastName(),
                Boolean.TRUE.equals(u.isEnabled()),
                roles,
                passwordChangeRequired);
    }
}
