package com.mes.iam.keycloak;

import com.mes.iam.exception.InvalidCredentialsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Verifies a user's current password via the Keycloak resource owner password grant
 * without creating a long-lived admin session. Used exclusively by the
 * change-temporary-password flow to confirm identity before setting a new password.
 */
@Component
public class KeycloakTokenClient {

    private final RestTemplate restTemplate;
    private final String tokenEndpoint;
    private final String clientId;

    public KeycloakTokenClient(
            RestTemplate restTemplate,
            @Value("${keycloak.admin.server-url:http://localhost:8080}") String serverUrl,
            @Value("${keycloak.admin.realm:mes}") String realm,
            @Value("${keycloak.direct-grant-client-id:mes-frontend}") String clientId) {
        this.restTemplate = restTemplate;
        this.tokenEndpoint = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";
        this.clientId = clientId;
    }

    /**
     * Attempts a password grant for the given credentials.
     *
     * @return {@code true} when KC responds with "Account is not fully set up"
     *         (correct password, but UPDATE_PASSWORD action is pending)
     * @throws InvalidCredentialsException when KC responds with any other error
     *         (wrong password, unknown user, or grant succeeded meaning
     *         the password is already permanent and no change is required)
     */
    public boolean verifyTemporaryPasswordCredentials(String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", clientId);
        form.add("username", username);
        form.add("password", password);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            restTemplate.postForEntity(tokenEndpoint, new HttpEntity<>(form, headers), String.class);
            // HTTP 200 → credentials valid but password is permanent — no change required
            throw new InvalidCredentialsException();
        } catch (HttpClientErrorException e) {
            // KC returns 400 (RFC 6749 compliant) or 401 for invalid_grant.
            // Distinguish by body, not by status code.
            String body = e.getResponseBodyAsString();
            if (body != null && body.contains("Account is not fully set up")) {
                return true;
            }
            throw new InvalidCredentialsException();
        }
    }
}
