package com.mikemes.iam.exception;

public class DuplicateRoleNameException extends RuntimeException {

    public DuplicateRoleNameException(String name) {
        super("A role named '" + name + "' already exists in this organisation");
    }
}
