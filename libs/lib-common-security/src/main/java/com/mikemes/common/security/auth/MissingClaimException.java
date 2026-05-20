package com.mikemes.common.security.auth;

public class MissingClaimException extends RuntimeException {

    public MissingClaimException(String claimName) {
        super("Required JWT claim is missing: " + claimName);
    }
}
