package com.mes.labour.skill.api.dto;

import com.mes.labour.skill.domain.Skill;

public final class SkillMapper {

    private SkillMapper() {
    }

    public static SkillDto toDto(Skill skill) {
        SkillDto dto = new SkillDto();
        dto.setId(skill.getId());
        dto.setOrgId(skill.getOrgId());
        dto.setSkillCode(skill.getSkillCode());
        dto.setName(skill.getName());
        dto.setDescription(skill.getDescription());
        dto.setCategory(skill.getCategory());
        dto.setCertificationRequired(skill.isCertificationRequired());
        dto.setValidityMonths(skill.getValidityMonths());
        dto.setActive(skill.isActive());
        dto.setCustomFields(skill.getCustomFields());
        dto.setCreatedBy(skill.getCreatedBy());
        dto.setCreatedAt(skill.getCreatedAt());
        dto.setModifiedBy(skill.getModifiedBy());
        dto.setModifiedAt(skill.getModifiedAt());
        return dto;
    }
}
