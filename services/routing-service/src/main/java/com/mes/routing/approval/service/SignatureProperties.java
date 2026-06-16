package com.mes.routing.approval.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Keycloak Direct-Access-Grant config for verifying an approver's password at e-signature time. */
@ConfigurationProperties(prefix = "mes.signature.keycloak")
public class SignatureProperties {

    /** Keycloak token endpoint (…/protocol/openid-connect/token). */
    private String tokenUri;

    /** Confidential client id used solely for password verification. */
    private String clientId = "mes-signature-verify";

    /** Client secret (MES_SIGNATURE_VERIFY_SECRET). */
    private String clientSecret;

    public String getTokenUri() {
        return tokenUri;
    }

    public void setTokenUri(String tokenUri) {
        this.tokenUri = tokenUri;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }
}
