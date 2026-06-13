package com.mes.labour.certification.service;

import com.mes.labour.certification.api.dto.EvaluateQualificationRequest;
import com.mes.labour.certification.api.dto.QualificationResultDto;
import com.mes.labour.certification.domain.Certification;
import com.mes.labour.certification.domain.CertificationState;
import com.mes.labour.certification.repository.CertificationRepository;
import com.mes.labour.employee.domain.Employee;
import com.mes.labour.employee.domain.EmploymentStatus;
import com.mes.labour.employee.repository.EmployeeRepository;
import com.mes.labour.service.LabourNotFoundException;
import com.mes.labour.skill.domain.Skill;
import com.mes.labour.skill.repository.SkillRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class QualificationService {

    private final CertificationRepository certificationRepository;
    private final EmployeeRepository employeeRepository;
    private final SkillRepository skillRepository;
    private final int warningDays;

    public QualificationService(CertificationRepository certificationRepository,
                                EmployeeRepository employeeRepository,
                                SkillRepository skillRepository,
                                @Value("${mes.labour.expiry-warning-days:30}") int warningDays) {
        this.certificationRepository = certificationRepository;
        this.employeeRepository = employeeRepository;
        this.skillRepository = skillRepository;
        this.warningDays = warningDays;
    }

    public QualificationResultDto evaluate(UUID orgId, EvaluateQualificationRequest req) {
        boolean hasEmployeeId = req.getEmployeeId() != null;
        boolean hasIamUserId = req.getIamUserId() != null && !req.getIamUserId().isBlank();
        if (hasEmployeeId == hasIamUserId) {
            throw new IllegalArgumentException(
                    "Exactly one of employeeId or iamUserId must be provided");
        }

        Employee employee = hasEmployeeId
                ? employeeRepository.findByOrgIdAndId(orgId, req.getEmployeeId())
                        .orElseThrow(() -> new LabourNotFoundException(
                                "Employee not found: " + req.getEmployeeId()))
                : employeeRepository.findByOrgIdAndIamUserId(orgId, req.getIamUserId())
                        .orElseThrow(() -> new LabourNotFoundException(
                                "No employee linked to IAM user: " + req.getIamUserId()));

        QualificationResultDto result = new QualificationResultDto();
        result.setEmployeeId(employee.getId());
        result.setEmployeeActive(employee.getEmploymentStatus() == EmploymentStatus.ACTIVE);

        List<UUID> skillIds = req.getSkillIds();
        if (skillIds.isEmpty()) {
            result.setResults(List.of());
            return result;
        }

        Map<UUID, Skill> skills = skillRepository.findAllByOrgIdAndIdIn(orgId, skillIds).stream()
                .collect(Collectors.toMap(Skill::getId, Function.identity()));

        Map<UUID, List<Certification>> certsBySkill = certificationRepository
                .findAllForEmployeeAndSkills(orgId, employee.getId(), skillIds).stream()
                .collect(Collectors.groupingBy(c -> c.getSkill().getId()));

        LocalDate today = LocalDate.now();
        List<QualificationResultDto.SkillResult> results = new ArrayList<>();
        for (UUID skillId : skillIds) {
            Skill skill = skills.get(skillId);
            if (skill == null) {
                throw new LabourNotFoundException("Skill not found: " + skillId);
            }
            results.add(evaluateSkill(skill, certsBySkill.get(skillId), today));
        }
        result.setResults(results);
        return result;
    }

    private QualificationResultDto.SkillResult evaluateSkill(
            Skill skill, List<Certification> certs, LocalDate today) {

        QualificationResultDto.SkillResult sr = new QualificationResultDto.SkillResult();
        sr.setSkillId(skill.getId());
        sr.setSkillCode(skill.getSkillCode());

        if (!skill.isActive()) {
            sr.setStatus("SKILL_INACTIVE");
            return sr;
        }
        if (certs == null || certs.isEmpty()) {
            sr.setStatus("NOT_HELD");
            return sr;
        }

        Optional<Certification> governing = CertificationStateCalculator.governing(certs);
        if (governing.isEmpty()) {
            // every certification for this skill is revoked
            sr.setStatus(CertificationState.REVOKED.name());
            return sr;
        }

        Certification cert = governing.get();
        CertificationState state = CertificationStateCalculator.stateOf(cert, today, warningDays);
        sr.setStatus(state == CertificationState.ACTIVE ? "HELD_ACTIVE" : state.name());
        sr.setExpiryDate(cert.getExpiryDate());
        return sr;
    }
}
