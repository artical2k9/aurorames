package com.mes.engineering.workinstruction.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Result of evaluating an operator against a revision's skill requirements.
 * {@code reason} is {@code OK} when labour-service answered, or {@code VERIFICATION_UNAVAILABLE}
 * when it could not be reached (fail-closed: {@code qualified} is then {@code false}).
 */
public record QualificationResultDto(boolean qualified, List<MissingSkill> missing, String reason) {

    /** A required skill the operator does not currently hold (with the labour-reported state). */
    public record MissingSkill(UUID skillId, String skillCode, String state) {
    }
}
