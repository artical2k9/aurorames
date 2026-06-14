package com.mes.engineering.workinstruction.api.dto;

import java.util.UUID;

/** A skill required by a work-instruction revision, with denormalised code/name. */
public record SkillRequirementDto(UUID id, UUID skillId, String skillCode, String skillName) {
}
