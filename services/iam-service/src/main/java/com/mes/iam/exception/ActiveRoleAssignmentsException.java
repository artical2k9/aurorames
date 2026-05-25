package com.mes.iam.exception;

public class ActiveRoleAssignmentsException extends RuntimeException {

    private final long userCount;

    public ActiveRoleAssignmentsException(long userCount) {
        super("Role has " + userCount + " active user assignment(s) — unassign users before deleting");
        this.userCount = userCount;
    }

    public long getUserCount() {
        return userCount;
    }
}
