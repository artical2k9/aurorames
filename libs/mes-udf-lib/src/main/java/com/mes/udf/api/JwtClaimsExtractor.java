package com.mes.udf.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

public final class JwtClaimsExtractor {

    private JwtClaimsExtractor() {}

    public static UUID orgId(Jwt jwt) {
        String raw = jwt.getClaimAsString("org_id");
        if (raw == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "org_id claim missing from token");
        }
        return UUID.fromString(raw);
    }
}
