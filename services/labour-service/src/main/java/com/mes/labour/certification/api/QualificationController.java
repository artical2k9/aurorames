package com.mes.labour.certification.api;

import com.mes.common.security.annotation.RequiresPrivilege;
import com.mes.labour.certification.api.dto.EvaluateQualificationRequest;
import com.mes.labour.certification.api.dto.QualificationResultDto;
import com.mes.labour.certification.service.QualificationService;
import com.mes.udf.api.JwtClaimsExtractor;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/labour/qualifications")
public class QualificationController {

    private final QualificationService qualificationService;

    public QualificationController(QualificationService qualificationService) {
        this.qualificationService = qualificationService;
    }

    @PostMapping("/evaluate")
    @RequiresPrivilege("labour:qualification:view")
    public QualificationResultDto evaluate(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EvaluateQualificationRequest request) {

        return qualificationService.evaluate(JwtClaimsExtractor.orgId(jwt), request);
    }
}
