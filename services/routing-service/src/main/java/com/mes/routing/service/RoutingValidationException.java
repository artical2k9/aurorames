package com.mes.routing.service;

/** Thrown on a domain validation failure (e.g. mutually-exclusive set rules). Maps to HTTP 422. */
public class RoutingValidationException extends RuntimeException {
    public RoutingValidationException(String message) {
        super(message);
    }
}
