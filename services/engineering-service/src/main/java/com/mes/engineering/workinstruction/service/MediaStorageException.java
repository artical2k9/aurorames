package com.mes.engineering.workinstruction.service;

/** Wraps low-level object-store failures (HTTP 500 — distinct from 422 validation failures). */
public class MediaStorageException extends RuntimeException {
    public MediaStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
