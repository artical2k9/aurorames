package com.mes.labour.certification.service;

import com.mes.labour.certification.api.dto.AwardCertificationRequest;
import com.mes.labour.certification.api.dto.CertificationDto;
import com.mes.labour.certification.api.dto.CertificationMapper;
import com.mes.labour.certification.domain.Certification;
import com.mes.labour.certification.domain.CertificationState;
import com.mes.labour.certification.repository.CertificationRepository;
import com.mes.labour.employee.domain.Employee;
import com.mes.labour.employee.domain.EmploymentStatus;
import com.mes.labour.employee.repository.EmployeeRepository;
import com.mes.labour.service.LabourConflictException;
import com.mes.labour.service.LabourNotFoundException;
import com.mes.labour.service.LabourValidationException;
import com.mes.labour.skill.domain.Skill;
import com.mes.labour.skill.repository.SkillRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CertificationService {

    private final CertificationRepository certificationRepository;
    private final EmployeeRepository employeeRepository;
    private final SkillRepository skillRepository;
    private final int warningDays;

    public CertificationService(CertificationRepository certificationRepository,
                                EmployeeRepository employeeRepository,
                                SkillRepository skillRepository,
                                @Value("${mes.labour.expiry-warning-days:30}") int warningDays) {
        this.certificationRepository = certificationRepository;
        this.employeeRepository = employeeRepository;
        this.skillRepository = skillRepository;
        this.warningDays = warningDays;
    }

    public CertificationDto award(UUID orgId, AwardCertificationRequest req) {
        Employee employee = employeeRepository.findByOrgIdAndId(orgId, req.getEmployeeId())
                .orElseThrow(() -> new LabourNotFoundException(
                        "Employee not found: " + req.getEmployeeId()));
        if (employee.getEmploymentStatus() == EmploymentStatus.INACTIVE) {
            throw new LabourValidationException(
                    "Cannot award certification to an inactive employee");
        }
        Skill skill = skillRepository.findByOrgIdAndId(orgId, req.getSkillId())
                .orElseThrow(() -> new LabourNotFoundException(
                        "Skill not found: " + req.getSkillId()));
        if (!skill.isActive()) {
            throw new LabourValidationException(
                    "Cannot award certification against an inactive skill: " + skill.getSkillCode());
        }
        if (certificationRepository.existsByEmployeeIdAndSkillIdAndAwardDate(
                employee.getId(), skill.getId(), req.getAwardDate())) {
            throw new LabourConflictException(
                    "Certification already awarded for this employee, skill and award date");
        }

        Certification cert = new Certification();
        cert.setOrgId(orgId);
        cert.setEmployee(employee);
        cert.setSkill(skill);
        cert.setAwardDate(req.getAwardDate());
        cert.setExpiryDate(resolveExpiry(req, skill));
        cert.setAssessor(req.getAssessor());
        cert.setEvidenceRef(req.getEvidenceRef());
        cert.setCustomFields(req.getCustomFields());
        Certification saved = certificationRepository.save(cert);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public CertificationDto get(UUID orgId, UUID certificationId) {
        return toDto(requireCertification(orgId, certificationId));
    }

    @Transactional(readOnly = true)
    public List<CertificationDto> list(UUID orgId, UUID employeeId, UUID skillId,
                                       Integer expiringWithinDays) {
        LocalDate today = LocalDate.now();
        return certificationRepository.findAllFiltered(orgId, employeeId, skillId).stream()
                .filter(c -> withinExpiryWindow(c, today, expiringWithinDays))
                .map(this::toDto)
                .toList();
    }

    public CertificationDto revoke(UUID orgId, UUID certificationId, String reason, String actor) {
        if (reason == null || reason.isBlank()) {
            throw new LabourValidationException("Revocation reason is required");
        }
        Certification cert = requireCertification(orgId, certificationId);
        cert.setRevoked(true);
        cert.setRevokedBy(actor);
        cert.setRevokedAt(Instant.now());
        cert.setRevocationReason(reason);
        return toDto(certificationRepository.save(cert));
    }

    @Transactional(readOnly = true)
    public List<CertificationDto> profileFor(UUID orgId, UUID employeeId) {
        employeeRepository.findByOrgIdAndId(orgId, employeeId)
                .orElseThrow(() -> new LabourNotFoundException("Employee not found: " + employeeId));
        return certificationRepository.findAllForEmployee(orgId, employeeId).stream()
                .map(this::toDto)
                .toList();
    }

    private LocalDate resolveExpiry(AwardCertificationRequest req, Skill skill) {
        if (req.getExpiryDate() != null) {
            return req.getExpiryDate();
        }
        if (skill.getValidityMonths() != null) {
            return req.getAwardDate().plusMonths(skill.getValidityMonths());
        }
        return null;
    }

    private boolean withinExpiryWindow(Certification cert, LocalDate today, Integer days) {
        if (days == null) {
            return true;
        }
        LocalDate expiry = cert.getExpiryDate();
        return !cert.isRevoked()
                && expiry != null
                && !expiry.isBefore(today)
                && !expiry.isAfter(today.plusDays(days));
    }

    private CertificationDto toDto(Certification cert) {
        CertificationState state =
                CertificationStateCalculator.stateOf(cert, LocalDate.now(), warningDays);
        return CertificationMapper.toDto(cert, state);
    }

    private Certification requireCertification(UUID orgId, UUID certificationId) {
        return certificationRepository.findByOrgIdAndId(orgId, certificationId)
                .orElseThrow(() -> new LabourNotFoundException(
                        "Certification not found: " + certificationId));
    }
}
