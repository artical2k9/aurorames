package com.mes.routing.kafka;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when a route revision becomes APPROVED (FR-024) so downstream Work Orders can consume it.
 * Serialised as JSON to {@code routing.route.approved}.
 */
public record RouteApprovedEvent(
        UUID routeId,
        UUID partId,
        String partRevision,
        int routeRevision,
        String approvedBy,
        Instant approvedAt) {
}
