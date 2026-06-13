package com.mes.labour.certification.api;

import com.mes.common.security.annotation.RequiresPrivilege;
import com.mes.labour.certification.api.dto.AwardCertificationRequest;
import com.mes.labour.certification.api.dto.CertificationDto;
import com.mes.labour.certification.service.CertificationService;
import com.mes.labour.training.service.TrainingService;
import com.mes.udf.api.JwtClaimsExtractor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/labour/certifications")
public class CertificationController {

    private final CertificationService certificationService;
    private final TrainingService trainingService;

    public CertificationController(CertificationService certificationService,
                                   TrainingService trainingService) {
        this.certificationService = certificationService;
        this.trainingService = trainingService;
    }

    @PostMapping
    @RequiresPrivilege("labour:certification:manage")
    public ResponseEntity<CertificationDto> award(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AwardCertificationRequest request) {

        CertificationDto dto = certificationService.award(JwtClaimsExtractor.orgId(jwt), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(dto.getId()).toUri();
        return ResponseEntity.created(location).body(dto);
    }

    @GetMapping
    @RequiresPrivilege("labour:certification:view")
    public Map<String, Object> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) UUID skillId,
            @RequestParam(required = false) Integer expiringWithinDays) {

        List<CertificationDto> content = certificationService.list(
                JwtClaimsExtractor.orgId(jwt), employeeId, skillId, expiringWithinDays);
        return Map.of("content", content, "totalElements", content.size());
    }

    @GetMapping("/{certificationId}")
    @RequiresPrivilege("labour:certification:view")
    public CertificationDto get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID certificationId) {

        UUID orgId = JwtClaimsExtractor.orgId(jwt);
        CertificationDto dto = certificationService.get(orgId, certificationId);
        dto.setSupportingTraining(trainingService.supportingTraining(
                orgId, dto.getEmployeeId(), dto.getSkillId()));
        return dto;
    }

    @PostMapping("/{certificationId}/revoke")
    @RequiresPrivilege("labour:certification:manage")
    public CertificationDto revoke(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID certificationId,
            @RequestBody(required = false) Map<String, String> body) {

        String reason = body != null ? body.get("reason") : null;
        return certificationService.revoke(JwtClaimsExtractor.orgId(jwt), certificationId,
                reason, JwtClaimsExtractor.nullSafeSubject(jwt));
    }
}
