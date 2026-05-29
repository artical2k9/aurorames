package com.mes.iam.api;

import com.mes.common.security.annotation.RequiresPrivilege;
import com.mes.iam.api.dto.PrivilegeResponse;
import com.mes.iam.api.dto.RegisterPrivilegesRequest;
import com.mes.iam.domain.Privilege;
import com.mes.iam.service.PrivilegeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class PrivilegeController {

    private final PrivilegeService privilegeService;

    public PrivilegeController(PrivilegeService privilegeService) {
        this.privilegeService = privilegeService;
    }

    @PostMapping("/internal/privileges/register")
    public ResponseEntity<Void> registerPrivileges(@Valid @RequestBody RegisterPrivilegesRequest request) {
        privilegeService.registerManifest(request.moduleName(), request.privileges());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/privileges")
    @RequiresPrivilege("iam:roles:manage")
    public Map<String, List<PrivilegeResponse>> listPrivileges() {
        return privilegeService.listByModule().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream().map(PrivilegeController::toResponse).toList()));
    }

    @GetMapping("/roles/privilege-map")
    public Map<String, List<String>> getPrivilegeMap() {
        return privilegeService.getPrivilegeMap();
    }

    private static PrivilegeResponse toResponse(Privilege p) {
        return new PrivilegeResponse(
                p.getId(),
                p.getPrivilegeKey(),
                p.getModuleName(),
                p.getDescription(),
                p.getRegisteredAt());
    }
}
