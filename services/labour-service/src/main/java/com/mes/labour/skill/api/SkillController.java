package com.mes.labour.skill.api;

import com.mes.common.security.annotation.RequiresPrivilege;
import com.mes.labour.skill.api.dto.CreateSkillRequest;
import com.mes.labour.skill.api.dto.PatchSkillRequest;
import com.mes.labour.skill.api.dto.SkillDto;
import com.mes.labour.skill.service.SkillService;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/labour/skills")
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @PostMapping
    @RequiresPrivilege("labour:skill:manage")
    public ResponseEntity<SkillDto> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateSkillRequest request) {

        SkillDto dto = skillService.create(JwtClaimsExtractor.orgId(jwt), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(dto.getId()).toUri();
        return ResponseEntity.created(location).body(dto);
    }

    @GetMapping
    @RequiresPrivilege("labour:skill:view")
    public Page<SkillDto> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) List<UUID> ids,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return skillService.list(JwtClaimsExtractor.orgId(jwt), search, active, ids,
                PageRequest.of(page, size));
    }

    @GetMapping("/{skillId}")
    @RequiresPrivilege("labour:skill:view")
    public SkillDto get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID skillId) {

        return skillService.get(JwtClaimsExtractor.orgId(jwt), skillId);
    }

    @PatchMapping("/{skillId}")
    @RequiresPrivilege("labour:skill:manage")
    public SkillDto patch(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID skillId,
            @Valid @RequestBody PatchSkillRequest request) {

        return skillService.patch(JwtClaimsExtractor.orgId(jwt), skillId, request);
    }
}
