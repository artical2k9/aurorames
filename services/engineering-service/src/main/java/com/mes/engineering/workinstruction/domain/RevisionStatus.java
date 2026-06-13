package com.mes.engineering.workinstruction.domain;

/** Lifecycle of a work-instruction revision (mirrors the ECO/BOM controlled-document pattern). */
public enum RevisionStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED
}
