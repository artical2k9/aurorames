package com.mikemes.iam.keycloak;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RoleRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KeycloakAdminClient {

    private final Keycloak keycloak;
    private final String realm;

    public KeycloakAdminClient(
            Keycloak keycloak,
            @Value("${keycloak.admin.realm:mikemes}") String realm) {
        this.keycloak = keycloak;
        this.realm = realm;
    }

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
}
