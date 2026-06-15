package com.mes.routing.service;

/** Thrown when a requested routing resource does not exist (or is out of org scope). Maps to HTTP 404. */
public class RoutingNotFoundException extends RuntimeException {
    public RoutingNotFoundException(String message) {
        super(message);
    }
}
