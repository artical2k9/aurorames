package com.mikemes.common.security.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.DockerClientFactory;

import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class KeycloakTestSupport implements BeforeAllCallback, AfterAllCallback {

    public static final String REALM = "mikemes-test";
    public static final String CLIENT_ID = "test-client";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private KeycloakContainer container;
    private Keycloak adminClient;

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            return;
        }
        container = new KeycloakContainer();
        container.start();
        adminClient = KeycloakBuilder.builder()
                .serverUrl(container.getAuthServerUrl())
                .realm("master")
                .clientId("admin-cli")
                .username(container.getAdminUsername())
                .password(container.getAdminPassword())
                .build();
        setupRealm();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (adminClient != null) {
            adminClient.close();
        }
        if (container != null) {
            container.stop();
        }
    }

    public boolean isRunning() {
        return container != null && container.isRunning();
    }

    public String issuerUri() {
        return container.getAuthServerUrl() + "/realms/" + REALM;
    }

    public void createUser(String username, String password, String orgId, List<String> roles) {
        for (String role : roles) {
            ensureRealmRole(role);
        }

        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEnabled(true);
        user.setEmail(username + "@test.mikemes.local");
        user.setEmailVerified(true);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setAttributes(Map.of("org_id", List.of(orgId)));

        Response userResponse = adminClient.realm(REALM).users().create(user);
        String userId = extractId(userResponse);
        userResponse.close();

        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setType(CredentialRepresentation.PASSWORD);
        cred.setValue(password);
        cred.setTemporary(false);
        adminClient.realm(REALM).users().get(userId).resetPassword(cred);

        List<RoleRepresentation> roleReps = roles.stream()
                .map(r -> adminClient.realm(REALM).roles().get(r).toRepresentation())
                .toList();
        adminClient.realm(REALM).users().get(userId).roles().realmLevel().add(roleReps);
    }

    public String fetchToken(String username, String password) {
        String formBody = "grant_type=password"
                + "&client_id=" + encode(CLIENT_ID)
                + "&username=" + encode(username)
                + "&password=" + encode(password);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(issuerUri() + "/protocol/openid-connect/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();
            String body = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString())
                    .body();
            com.fasterxml.jackson.databind.JsonNode node = MAPPER.readTree(body).get("access_token");
            if (node == null) {
                throw new IllegalStateException("No access_token in response: " + body);
            }
            return node.asText();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("fetchToken failed", e);
        }
    }

    private void setupRealm() {
        RealmRepresentation realm = new RealmRepresentation();
        realm.setRealm(REALM);
        realm.setEnabled(true);
        realm.setDirectGrantFlow("direct grant");
        adminClient.realms().create(realm);

        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(CLIENT_ID);
        client.setPublicClient(true);
        client.setDirectAccessGrantsEnabled(true);
        client.setEnabled(true);

        Response clientResponse = adminClient.realm(REALM).clients().create(client);
        String clientUuid = extractId(clientResponse);
        clientResponse.close();

        Response r1 = adminClient.realm(REALM).clients().get(clientUuid)
                .getProtocolMappers().createMapper(buildRolesMapper());
        r1.close();

        Response r2 = adminClient.realm(REALM).clients().get(clientUuid)
                .getProtocolMappers().createMapper(buildOrgIdMapper());
        r2.close();
    }

    private void ensureRealmRole(String roleName) {
        try {
            adminClient.realm(REALM).roles().get(roleName).toRepresentation();
        } catch (Exception e) {
            RoleRepresentation role = new RoleRepresentation();
            role.setName(roleName);
            adminClient.realm(REALM).roles().create(role);
        }
    }

    private static String extractId(Response response) {
        String location = response.getHeaderString("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static ProtocolMapperRepresentation buildRolesMapper() {
        ProtocolMapperRepresentation mapper = new ProtocolMapperRepresentation();
        mapper.setName("roles-claim");
        mapper.setProtocol("openid-connect");
        mapper.setProtocolMapper("oidc-usermodel-realm-role-mapper");
        mapper.setConfig(Map.of(
                "multivalued", "true",
                "access.token.claim", "true",
                "userinfo.token.claim", "false",
                "claim.name", "roles",
                "jsonType.label", "String"));
        return mapper;
    }

    private static ProtocolMapperRepresentation buildOrgIdMapper() {
        ProtocolMapperRepresentation mapper = new ProtocolMapperRepresentation();
        mapper.setName("org-id-claim");
        mapper.setProtocol("openid-connect");
        mapper.setProtocolMapper("oidc-hardcoded-claim-mapper");
        mapper.setConfig(Map.of(
                "claim.value", "00000000-0000-0000-0000-000000000001",
                "access.token.claim", "true",
                "claim.name", "org_id",
                "jsonType.label", "String"));
        return mapper;
    }
}
