package com.mes.routing.approval.domain;

/** What an {@link ApprovalRecord} approves: a whole route revision, or a single operation revision. */
public enum ApprovalSubjectType {
    ROUTE_REVISION,
    OPERATION_REVISION
}
