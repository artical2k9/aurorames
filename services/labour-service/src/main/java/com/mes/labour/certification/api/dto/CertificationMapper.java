package com.mes.labour.certification.api.dto;

import com.mes.labour.certification.domain.Certification;
import com.mes.labour.certification.domain.CertificationState;

public final class CertificationMapper {

    private CertificationMapper() {
    }

    public static CertificationDto toDto(Certification cert, CertificationState state) {
        CertificationDto dto = new CertificationDto();
        dto.setId(cert.getId());
        dto.setEmployeeId(cert.getEmployee().getId());
        dto.setEmployeeNumber(cert.getEmployee().getEmployeeNumber());
        dto.setSkillId(cert.getSkill().getId());
        dto.setSkillCode(cert.getSkill().getSkillCode());
        dto.setSkillName(cert.getSkill().getName());
        dto.setAwardDate(cert.getAwardDate());
        dto.setExpiryDate(cert.getExpiryDate());
        dto.setAssessor(cert.getAssessor());
        dto.setEvidenceRef(cert.getEvidenceRef());
        dto.setState(state.name());
        dto.setRevoked(cert.isRevoked());
        dto.setRevokedBy(cert.getRevokedBy());
        dto.setRevokedAt(cert.getRevokedAt());
        dto.setRevocationReason(cert.getRevocationReason());
        dto.setCustomFields(cert.getCustomFields());
        dto.setCreatedBy(cert.getCreatedBy());
        dto.setCreatedAt(cert.getCreatedAt());
        return dto;
    }
}
