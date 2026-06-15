package com.mes.routing.service;

/** Thrown on a routing uniqueness/state conflict (e.g. duplicate, in-use delete). Maps to HTTP 409. */
public class RoutingConflictException extends RuntimeException {
    public RoutingConflictException(String message) {
        super(message);
    }
}
