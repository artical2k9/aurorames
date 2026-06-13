package com.mes.engineering.workinstruction.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Verifies a user's password against Keycloak using the Resource Owner Password Credentials
 * (Direct Access Grant) flow on the mes-signature-verify confidential client. The token KC
 * returns on success is never read or stored — only the HTTP outcome matters (research.md R1).
 */
@Component
public class KeycloakCredentialVerifier {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakCredentialVerifier.class);

    private final RestClient restClient;
    private final SignatureProperties properties;

    public KeycloakCredentialVerifier(SignatureProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.create();
    }

    /** @return true if Keycloak authenticates {@code username}/{@code password}; false otherwise. */
    public boolean verify(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return false;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        form.add("username", username);
        form.add("password", password);
        form.add("scope", "openid");
        try {
            Boolean verified = restClient.post()
                    .uri(properties.getTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .exchange((request, response) -> response.getStatusCode().is2xxSuccessful());
            return Boolean.TRUE.equals(verified);
        } catch (RuntimeException ex) {
            // Network/config error or non-401 failure — fail closed (treat as not verified).
            LOG.warn("E-signature credential verification call failed: {}", ex.getMessage());
            return false;
        }
    }
}
