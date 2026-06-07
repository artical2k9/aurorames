package com.mes.iam.keycloak;

import com.mes.iam.exception.DuplicateEmailException;
import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class KeycloakAdminClient {

    private static final Pattern MES_ROLE = Pattern.compile("^[A-Z][A-Z0-9_]{0,99}$");

    private final Keycloak keycloak;
    private final String realm;

    public KeycloakAdminClient(
            Keycloak keycloak,
            @Value("${keycloak.admin.realm:mes}") String realm) {
        this.keycloak = keycloak;
        this.realm = realm;
    }

    // ── Role operations ───────────────────────────────────────────────────────

    public String createRole(String roleName) {
        RoleRepresentation role = new RoleRepresentation();
        role.setName(roleName);
        keycloak.realm(realm).roles().create(role);
        return keycloak.realm(realm).roles().get(roleName).toRepresentation().getId();
    }

    public void deleteRole(String roleName) {
        keycloak.realm(realm).roles().deleteRole(roleName);
    }

    public int countUsersWithRole(String roleName) {
        try {
            return keycloak.realm(realm).roles().get(roleName)
                    .getUserMembers(0, Integer.MAX_VALUE).size();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    public void assignRoleToUser(String userId, String roleName) {
        RoleRepresentation role = keycloak.realm(realm).roles().get(roleName).toRepresentation();
        keycloak.realm(realm).users().get(userId).roles().realmLevel().add(List.of(role));
    }

    public void removeRoleFromUser(String userId, String roleName) {
        RoleRepresentation role = keycloak.realm(realm).roles().get(roleName).toRepresentation();
        keycloak.realm(realm).users().get(userId).roles().realmLevel().remove(List.of(role));
    }

    // ── User operations ───────────────────────────────────────────────────────

    public String createUser(String email, String firstName, String lastName, UUID orgId) {
        UserRepresentation user = new UserRepresentation();
        user.setEmail(email);
        user.setUsername(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(true);
        user.setAttributes(Map.of("org_id", List.of(orgId.toString())));

        try (Response r = keycloak.realm(realm).users().create(user)) {
            if (r.getStatus() == Response.Status.CONFLICT.getStatusCode()) {
                throw new DuplicateEmailException(email);
            }
            return extractId(r);
        }
    }

    public void sendPasswordEmail(String userId) {
        try {
            keycloak.realm(realm).users().get(userId)
                    .executeActionsEmail(List.of("UPDATE_PASSWORD"));
        } catch (RuntimeException e) {
            // SMTP not configured in this environment — admin can trigger reset manually
        }
    }

    public Optional<UserRepresentation> findUserById(String userId) {
        try {
            return Optional.ofNullable(
                    keycloak.realm(realm).users().get(userId).toRepresentation());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public List<UserRepresentation> listUsersByOrgId(UUID orgId) {
        return keycloak.realm(realm).users()
                .searchByAttributes("org_id:" + orgId);
    }

    public List<String> getUserRoles(String userId) {
        return keycloak.realm(realm).users().get(userId)
                .roles().realmLevel().listAll().stream()
                .map(RoleRepresentation::getName)
                .filter(name -> MES_ROLE.matcher(name).matches())
                .toList();
    }

    public void assignUserRoles(String userId, List<String> roleNames) {
        List<RoleRepresentation> roles = roleNames.stream()
                .map(name -> keycloak.realm(realm).roles().get(name).toRepresentation())
                .toList();
        if (!roles.isEmpty()) {
            keycloak.realm(realm).users().get(userId).roles().realmLevel().add(roles);
        }
    }

    public void setUserRoles(String userId, List<String> roleNames) {
        List<RoleRepresentation> current = keycloak.realm(realm).users().get(userId)
                .roles().realmLevel().listAll();
        List<RoleRepresentation> currentMESRoles = current.stream()
                .filter(r -> MES_ROLE.matcher(r.getName()).matches())
                .toList();

        Set<String> desiredNames = new HashSet<>(roleNames);
        List<RoleRepresentation> toRemove = currentMESRoles.stream()
                .filter(r -> !desiredNames.contains(r.getName()))
                .toList();
        if (!toRemove.isEmpty()) {
            keycloak.realm(realm).users().get(userId).roles().realmLevel().remove(toRemove);
        }

        Set<String> currentNames = currentMESRoles.stream()
                .map(RoleRepresentation::getName)
                .collect(Collectors.toSet());
        List<RoleRepresentation> toAdd = roleNames.stream()
                .filter(name -> !currentNames.contains(name))
                .map(name -> keycloak.realm(realm).roles().get(name).toRepresentation())
                .toList();
        if (!toAdd.isEmpty()) {
            keycloak.realm(realm).users().get(userId).roles().realmLevel().add(toAdd);
        }
    }

    public void deactivateUser(String userId) {
        UserRepresentation patch = new UserRepresentation();
        patch.setEnabled(false);
        keycloak.realm(realm).users().get(userId).update(patch);
    }

    public void setPassword(String userId, String password, boolean temporary) {
        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setType(CredentialRepresentation.PASSWORD);
        cred.setValue(password);
        cred.setTemporary(temporary);
        keycloak.realm(realm).users().get(userId).resetPassword(cred);
    }

    public void clearRequiredActions(String userId) {
        UserRepresentation patch = new UserRepresentation();
        patch.setRequiredActions(List.of());
        keycloak.realm(realm).users().get(userId).update(patch);
    }

    public Optional<UserRepresentation> findUserByEmail(String email) {
        List<UserRepresentation> users = keycloak.realm(realm).users()
                .searchByUsername(email, true);
        return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String extractId(Response response) {
        String location = response.getHeaderString("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }
}
