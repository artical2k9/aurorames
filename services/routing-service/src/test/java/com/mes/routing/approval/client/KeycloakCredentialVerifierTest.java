package com.mes.routing.approval.client;

import com.mes.routing.approval.service.SignatureProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakCredentialVerifierTest {

    private KeycloakCredentialVerifier verifier(String tokenUri) {
        SignatureProperties props = new SignatureProperties();
        props.setTokenUri(tokenUri);
        props.setClientId("mes-signature-verify");
        props.setClientSecret("secret");
        return new KeycloakCredentialVerifier(props);
    }

    @Test
    void blankCredentials_returnFalseWithoutCallingKeycloak() {
        KeycloakCredentialVerifier verifier = verifier("http://localhost:1/token");
        assertThat(verifier.verify(null, "pw")).isFalse();
        assertThat(verifier.verify("", "pw")).isFalse();
        assertThat(verifier.verify("user", null)).isFalse();
        assertThat(verifier.verify("user", " ")).isFalse();
    }

    @Test
    void unreachableKeycloak_failsClosed() {
        // Port 1 refuses connections — the RestClient call throws and the verifier must return false.
        KeycloakCredentialVerifier verifier = verifier("http://localhost:1/token");
        assertThat(verifier.verify("user", "password")).isFalse();
    }
}
