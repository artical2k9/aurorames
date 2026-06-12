package com.mes.labour.employee.api;

import com.mes.common.security.annotation.RequiresPrivilege;
import com.mes.labour.employee.api.dto.CreateEmployeeRequest;
import com.mes.labour.employee.api.dto.EmployeeDto;
import com.mes.labour.employee.api.dto.PatchEmployeeRequest;
import com.mes.labour.employee.domain.EmploymentStatus;
import com.mes.labour.employee.service.EmployeeService;
import com.mes.udf.api.JwtClaimsExtractor;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/labour/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @PostMapping
    @RequiresPrivilege("labour:employee:manage")
    public ResponseEntity<EmployeeDto> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateEmployeeRequest request) {

        EmployeeDto dto = employeeService.create(JwtClaimsExtractor.orgId(jwt), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(dto.getId()).toUri();
        return ResponseEntity.created(location).body(dto);
    }

    @GetMapping
    @RequiresPrivilege("labour:employee:view")
    public Page<EmployeeDto> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) EmploymentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return employeeService.list(JwtClaimsExtractor.orgId(jwt), search, status,
                PageRequest.of(page, size));
    }

    @GetMapping("/{employeeId}")
    @RequiresPrivilege("labour:employee:view")
    public EmployeeDto get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID employeeId) {

        return employeeService.get(JwtClaimsExtractor.orgId(jwt), employeeId);
    }

    @GetMapping("/by-iam-user/{iamUserId}")
    @RequiresPrivilege("labour:employee:view")
    public EmployeeDto getByIamUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String iamUserId) {

        return employeeService.getByIamUserId(JwtClaimsExtractor.orgId(jwt), iamUserId);
    }

    @PatchMapping("/{employeeId}")
    @RequiresPrivilege("labour:employee:manage")
    public EmployeeDto patch(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID employeeId,
            @Valid @RequestBody PatchEmployeeRequest request) {

        return employeeService.patch(JwtClaimsExtractor.orgId(jwt), employeeId, request);
    }
}
