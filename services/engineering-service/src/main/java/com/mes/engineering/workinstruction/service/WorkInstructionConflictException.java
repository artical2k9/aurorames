package com.mes.engineering.workinstruction.service;

/** Thrown on a state conflict — duplicate identifier, edit of a non-DRAFT revision, etc. (409). */
public class WorkInstructionConflictException extends RuntimeException {
    public WorkInstructionConflictException(String message) {
        super(message);
    }
}
