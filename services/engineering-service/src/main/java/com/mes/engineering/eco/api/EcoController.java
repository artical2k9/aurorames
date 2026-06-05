package com.mes.engineering.eco.api;

import com.mes.common.security.annotation.RequiresPrivilege;
import com.mes.engineering.eco.api.dto.CreateEcoRequest;
import com.mes.engineering.eco.api.dto.EcoDto;
import com.mes.engineering.eco.service.EcoService;
import com.mes.udf.api.JwtClaimsExtractor;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ecos")
public class EcoController {

    private final EcoService ecoService;

    public EcoController(EcoService ecoService) {
        this.ecoService = ecoService;
    }

    @GetMapping
    @RequiresPrivilege("item-master:eco:manage")
    public Page<EcoDto> listEcos(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ecoService.list(JwtClaimsExtractor.orgId(jwt), PageRequest.of(page, size));
    }

    @PostMapping
    @RequiresPrivilege("item-master:eco:manage")
    public ResponseEntity<EcoDto> createEco(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateEcoRequest request) {

        var orgId = JwtClaimsExtractor.orgId(jwt);
        var dto = ecoService.create(orgId, request, subjectOf(jwt));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(dto.getId()).toUri();
        return ResponseEntity.created(location).body(dto);
    }

    @GetMapping("/{ecoId}")
    @RequiresPrivilege("item-master:eco:manage")
    public EcoDto getEco(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID ecoId) {

        return ecoService.getEco(JwtClaimsExtractor.orgId(jwt), ecoId);
    }

    @PostMapping("/{ecoId}/approve")
    @RequiresPrivilege("item-master:eco:manage")
    public EcoDto approveEco(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID ecoId) {

        return ecoService.approve(JwtClaimsExtractor.orgId(jwt), ecoId, subjectOf(jwt));
    }

    private static String subjectOf(Jwt jwt) {
        String sub = jwt.getSubject();
        if (sub != null && !sub.isBlank()) {
            return sub;
        }
        String username = jwt.getClaimAsString("preferred_username");
        return (username != null && !username.isBlank()) ? username : "unknown";
    }
}
