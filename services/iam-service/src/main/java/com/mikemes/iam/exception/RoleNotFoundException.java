package com.mikemes.iam.exception;

import java.util.UUID;

public class RoleNotFoundException extends RuntimeException {

    public RoleNotFoundException(UUID roleId) {
        super("Role not found: " + roleId);
    }
}
