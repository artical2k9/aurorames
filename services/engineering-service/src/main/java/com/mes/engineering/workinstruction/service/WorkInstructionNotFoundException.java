package com.mes.engineering.workinstruction.service;

/** Thrown when a work instruction, revision, step, or related resource is not found (404). */
public class WorkInstructionNotFoundException extends RuntimeException {
    public WorkInstructionNotFoundException(String message) {
        super(message);
    }
}
