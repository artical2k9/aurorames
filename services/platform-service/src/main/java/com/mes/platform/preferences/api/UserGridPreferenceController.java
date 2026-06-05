package com.mes.platform.preferences.api;

import com.mes.platform.preferences.api.dto.UpsertUserGridPreferenceRequest;
import com.mes.platform.preferences.api.dto.UserGridPreferenceDto;
import com.mes.platform.preferences.service.UserGridPreferenceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/preferences/grid")
public class UserGridPreferenceController {

    private final UserGridPreferenceService service;

    public UserGridPreferenceController(UserGridPreferenceService service) {
        this.service = service;
    }

    @GetMapping("/{moduleKey}")
    public ResponseEntity<UserGridPreferenceDto> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String moduleKey) {

        UUID orgId = extractOrgId(jwt);
        String userId = jwt.getSubject();
        return ResponseEntity.ok(service.get(orgId, userId, moduleKey));
    }

    @PutMapping("/{moduleKey}")
    public ResponseEntity<UserGridPreferenceDto> upsert(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String moduleKey,
            @Valid @RequestBody UpsertUserGridPreferenceRequest request) {

        UUID orgId = extractOrgId(jwt);
        String userId = jwt.getSubject();
        return ResponseEntity.ok(service.upsert(orgId, userId, moduleKey, request.columns()));
    }

    private UUID extractOrgId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("org_id"));
    }
}
