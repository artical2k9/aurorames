package com.mes.quality.service;

import java.util.List;

/**
 * Unprocessable entity (422). Carries an optional details list — used by the expression
 * validator and submit-gate to enumerate each bad reference / cycle / missing requirement.
 */
public class QualityValidationException extends RuntimeException {

    private final transient List<String> details;

    public QualityValidationException(String message) {
        this(message, List.of());
    }

    public QualityValidationException(String message, List<String> details) {
        super(message);
        this.details = details != null ? List.copyOf(details) : List.of();
    }

    public List<String> getDetails() {
        return details;
    }
}
