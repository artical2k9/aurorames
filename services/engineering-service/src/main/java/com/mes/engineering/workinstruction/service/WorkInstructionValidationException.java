package com.mes.engineering.workinstruction.service;

import java.util.List;

/**
 * Thrown on a business-rule validation failure (422). Carries optional detail messages
 * (e.g. signature verification failure code, zero-step submit). The {@code details} field is
 * non-transient: {@link List#copyOf} yields a serializable, immutable list (ERR-MES-083).
 */
public class WorkInstructionValidationException extends RuntimeException {

    private final List<String> details;

    public WorkInstructionValidationException(String message) {
        super(message);
        this.details = List.of();
    }

    public WorkInstructionValidationException(String message, List<String> details) {
        super(message);
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public List<String> getDetails() {
        return details;
    }
}
