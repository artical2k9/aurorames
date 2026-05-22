package com.mikemes.iam.exception;

import java.util.UUID;

public class PrivilegeNotFoundException extends RuntimeException {

    public PrivilegeNotFoundException(UUID privilegeId) {
        super("Privilege not found: " + privilegeId);
    }
}
