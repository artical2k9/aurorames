package com.mes.labour.skill.service;

import com.mes.labour.service.LabourConflictException;
import com.mes.labour.service.LabourNotFoundException;
import com.mes.labour.skill.api.dto.CreateSkillRequest;
import com.mes.labour.skill.api.dto.PatchSkillRequest;
import com.mes.labour.skill.api.dto.SkillDto;
import com.mes.labour.skill.api.dto.SkillMapper;
import com.mes.labour.skill.domain.Skill;
import com.mes.labour.skill.repository.SkillRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class SkillService {

    private final SkillRepository skillRepository;

    public SkillService(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    public SkillDto create(UUID orgId, CreateSkillRequest req) {
        if (skillRepository.existsByOrgIdAndSkillCode(orgId, req.getSkillCode())) {
            throw new LabourConflictException("Skill code already exists: " + req.getSkillCode());
        }
        Skill skill = new Skill();
        skill.setOrgId(orgId);
        skill.setSkillCode(req.getSkillCode());
        skill.setName(req.getName());
        skill.setDescription(req.getDescription());
        skill.setCategory(req.getCategory());
        if (req.getCertificationRequired() != null) {
            skill.setCertificationRequired(req.getCertificationRequired());
        }
        skill.setValidityMonths(req.getValidityMonths());
        skill.setCustomFields(req.getCustomFields());
        return SkillMapper.toDto(skillRepository.save(skill));
    }

    @Transactional(readOnly = true)
    public SkillDto get(UUID orgId, UUID skillId) {
        return SkillMapper.toDto(requireSkill(orgId, skillId));
    }

    @Transactional(readOnly = true)
    public Page<SkillDto> list(UUID orgId, String search, Boolean active, List<UUID> ids,
                               Pageable pageable) {
        if (ids != null && !ids.isEmpty()) {
            List<SkillDto> matched = skillRepository.findAllByOrgIdAndIdIn(orgId, ids).stream()
                    .filter(s -> active == null || s.isActive() == active)
                    .sorted(Comparator.comparing(Skill::getSkillCode))
                    .map(SkillMapper::toDto)
                    .toList();
            return new PageImpl<>(matched, pageable, matched.size());
        }
        String normalisedSearch = (search == null || search.isBlank()) ? null : search;
        return skillRepository.search(orgId, normalisedSearch, active, pageable)
                .map(SkillMapper::toDto);
    }

    public SkillDto patch(UUID orgId, UUID skillId, PatchSkillRequest req) {
        Skill skill = requireSkill(orgId, skillId);
        if (req.getName() != null) {
            skill.setName(req.getName());
        }
        if (req.getDescription() != null) {
            skill.setDescription(req.getDescription());
        }
        if (req.getCategory() != null) {
            skill.setCategory(req.getCategory());
        }
        if (req.getCertificationRequired() != null) {
            skill.setCertificationRequired(req.getCertificationRequired());
        }
        if (req.getValidityMonths() != null) {
            skill.setValidityMonths(req.getValidityMonths());
        }
        if (req.getActive() != null) {
            skill.setActive(req.getActive());
        }
        if (req.getCustomFields() != null) {
            skill.setCustomFields(req.getCustomFields());
        }
        return SkillMapper.toDto(skillRepository.save(skill));
    }

    private Skill requireSkill(UUID orgId, UUID skillId) {
        return skillRepository.findByOrgIdAndId(orgId, skillId)
                .orElseThrow(() -> new LabourNotFoundException("Skill not found: " + skillId));
    }
}
