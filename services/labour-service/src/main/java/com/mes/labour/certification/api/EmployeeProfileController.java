package com.mes.labour.certification.api;

import com.mes.common.security.annotation.RequiresPrivilege;
import com.mes.labour.certification.api.dto.CertificationDto;
import com.mes.labour.certification.service.CertificationService;
import com.mes.labour.employee.api.dto.EmployeeDto;
import com.mes.labour.employee.service.EmployeeService;
import com.mes.udf.api.JwtClaimsExtractor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Competency profile lives in the certification package because it aggregates
// certification states; the route stays under /employees for API ergonomics.
@RestController
@RequestMapping("/api/v1/labour/employees")
public class EmployeeProfileController {

    private final EmployeeService employeeService;
    private final CertificationService certificationService;

    public EmployeeProfileController(EmployeeService employeeService,
                                     CertificationService certificationService) {
        this.employeeService = employeeService;
        this.certificationService = certificationService;
    }

    @GetMapping("/{employeeId}/profile")
    @RequiresPrivilege("labour:employee:view")
    public Map<String, Object> profile(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID employeeId) {

        UUID orgId = JwtClaimsExtractor.orgId(jwt);
        EmployeeDto employee = employeeService.get(orgId, employeeId);
        List<CertificationDto> certifications = certificationService.profileFor(orgId, employeeId);
        return Map.of(
                "employee", employee,
                "certifications", certifications);
    }
}
