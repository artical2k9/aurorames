package com.mes.routing.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Upstream event indicating a BOM or inspection-plan revision has been superseded. Carries the
 * id of the revision that was superseded; routing flags any route pinned to it (FR-004f).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RevisionSupersededEvent(String revisionId) {
}
