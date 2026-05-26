package com.mes.udf.api;

import com.mes.common.security.annotation.RequiresPrivilege;
import com.mes.udf.api.dto.CreateUdfFieldRequest;
import com.mes.udf.api.dto.UdfFieldDefinitionDto;
import com.mes.udf.api.dto.UdfFieldDefinitionMapper;
import com.mes.udf.domain.ModuleKey;
import com.mes.udf.service.UdfFieldDefinitionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/udf/fields")
public class UdfFieldDefinitionController {

    private final UdfFieldDefinitionService service;

    public UdfFieldDefinitionController(UdfFieldDefinitionService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<UdfFieldDefinitionDto>> list(
            @RequestParam String module,
            @AuthenticationPrincipal Jwt jwt) {
        UUID orgId = extractOrgId(jwt);
        ModuleKey moduleKey = ModuleKey.valueOf(module);
        List<UdfFieldDefinitionDto> dtos = service.list(orgId, moduleKey).stream()
                .map(UdfFieldDefinitionMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    @RequiresPrivilege("item-master:udf:manage")
    public ResponseEntity<UdfFieldDefinitionDto> define(
            @Valid @RequestBody CreateUdfFieldRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID orgId = extractOrgId(jwt);
        var entity = service.define(orgId, request);
        URI location = URI.create("/udf/fields/" + entity.getId());
        return ResponseEntity.created(location).body(UdfFieldDefinitionMapper.toDto(entity));
    }

    @DeleteMapping("/{fieldId}")
    @RequiresPrivilege("item-master:udf:manage")
    public ResponseEntity<Void> deactivate(
            @PathVariable UUID fieldId,
            @RequestParam(defaultValue = "false") boolean force,
            @AuthenticationPrincipal Jwt jwt) {
        UUID orgId = extractOrgId(jwt);
        service.deactivate(orgId, fieldId, force);
        return ResponseEntity.noContent().build();
    }

    private UUID extractOrgId(Jwt jwt) {
        String orgId = jwt.getClaimAsString("org_id");
        if (orgId == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "org_id claim missing from token");
        }
        return UUID.fromString(orgId);
    }
}
