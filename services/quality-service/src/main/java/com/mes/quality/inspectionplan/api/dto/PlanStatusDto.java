package com.mes.quality.inspectionplan.api.dto;

/** Cheap release-gate status (MES-9). */
public record PlanStatusDto(
        boolean exists,
        boolean approved,
        Integer latestApprovedRevision) {
}
