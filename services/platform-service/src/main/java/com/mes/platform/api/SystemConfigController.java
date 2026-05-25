package com.mes.platform.api;

import com.mes.common.security.annotation.RequiresPrivilege;
import com.mes.common.security.auth.JwtClaimsExtractor;
import com.mes.common.security.auth.OrganisationContextHolder;
import com.mes.platform.api.dto.SystemConfigDto;
import com.mes.platform.api.dto.UpsertConfigRequest;
import com.mes.platform.service.SystemConfigService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/config")
public class SystemConfigController {

    private final SystemConfigService configService;

    public SystemConfigController(SystemConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/")
    @RequiresPrivilege("platform:config:read")
    public List<SystemConfigDto> listAll(Principal principal) {
        return configService.listAll(resolveOrgId(principal));
    }

    @GetMapping("/{key}")
    @RequiresPrivilege("platform:config:read")
    public SystemConfigDto getByKey(@PathVariable String key, Principal principal) {
        return configService.findByKey(resolveOrgId(principal), key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Config key not found: " + key));
    }

    @PutMapping("/{key}")
    @RequiresPrivilege("platform:config:manage")
    public SystemConfigDto upsert(@PathVariable String key,
                                   @Valid @RequestBody UpsertConfigRequest request,
                                   Principal principal) {
        UUID orgId = resolveOrgId(principal);
        String sub = extractSub(principal);
        return configService.upsert(orgId, key, request, sub);
    }

    @DeleteMapping("/{key}")
    @RequiresPrivilege("platform:config:manage")
    public ResponseEntity<Void> softDelete(@PathVariable String key, Principal principal) {
        configService.softDelete(resolveOrgId(principal), key);
        return ResponseEntity.noContent().build();
    }

    private UUID resolveOrgId(Principal principal) {
        return OrganisationContextHolder.getOrgId()
                .map(UUID::fromString)
                .orElseGet(() -> {
                    if (principal instanceof JwtAuthenticationToken jwtToken) {
                        String orgId = ((Jwt) jwtToken.getPrincipal()).getClaimAsString("org_id");
                        if (orgId != null) {
                            return UUID.fromString(orgId);
                        }
                    }
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing org_id claim");
                });
    }

    private String extractSub(Principal principal) {
        if (principal instanceof JwtAuthenticationToken jwtToken) {
            return new JwtClaimsExtractor((Jwt) jwtToken.getPrincipal()).getSub();
        }
        return "unknown";
    }
}
