package com.mes.workorder.bom.api;

import com.mes.common.security.annotation.RequiresPrivilege;
import com.mes.udf.api.JwtClaimsExtractor;
import com.mes.workorder.bom.api.dto.BomDto;
import com.mes.workorder.bom.api.dto.BomExplosionNode;
import com.mes.workorder.bom.api.dto.BomLineDto;
import com.mes.workorder.bom.api.dto.BomMapper;
import com.mes.workorder.bom.api.dto.CreateBomLineRequest;
import com.mes.workorder.bom.api.dto.CreateBomRequest;
import com.mes.workorder.bom.api.dto.UpdateBomLineRequest;
import com.mes.workorder.bom.service.BomExplosionService;
import com.mes.workorder.bom.service.BomService;
import jakarta.validation.Valid;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/boms")
public class BomController {

    private final BomService bomService;
    private final BomExplosionService explosionService;

    public BomController(BomService bomService, BomExplosionService explosionService) {
        this.bomService = bomService;
        this.explosionService = explosionService;
    }

    @PostMapping
    @RequiresPrivilege("item-master:bom:manage")
    public ResponseEntity<BomDto> createBom(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateBomRequest request) {

        var orgId = JwtClaimsExtractor.orgId(jwt);
        var bom = bomService.createBom(orgId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(bom.getId()).toUri();
        return ResponseEntity.created(location).body(BomMapper.toDto(bom));
    }

    @GetMapping("/{bomId}")
    @RequiresPrivilege("item-master:bom:manage")
    public BomDto getBom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bomId) {

        return BomMapper.toDto(bomService.getBom(JwtClaimsExtractor.orgId(jwt), bomId));
    }

    @PostMapping("/{bomId}/release")
    @RequiresPrivilege("item-master:bom:manage")
    public BomDto releaseBom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bomId) {

        return BomMapper.toDto(bomService.releaseBom(JwtClaimsExtractor.orgId(jwt), bomId));
    }

    @GetMapping("/{bomId}/lines")
    @RequiresPrivilege("item-master:bom:manage")
    public List<BomLineDto> listLines(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bomId) {

        var orgId = JwtClaimsExtractor.orgId(jwt);
        return bomService.listLines(orgId, bomId).stream()
                .map(BomMapper::toLineDto)
                .toList();
    }

    @PostMapping("/{bomId}/lines")
    @RequiresPrivilege("item-master:bom:manage")
    public ResponseEntity<BomLineDto> addLine(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bomId,
            @Valid @RequestBody CreateBomLineRequest request) {

        var orgId = JwtClaimsExtractor.orgId(jwt);
        var line = bomService.addLine(orgId, bomId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(line.getId()).toUri();
        return ResponseEntity.created(location).body(BomMapper.toLineDto(line));
    }

    @PatchMapping("/{bomId}/lines/{lineId}")
    @RequiresPrivilege("item-master:bom:manage")
    public BomLineDto updateLine(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bomId,
            @PathVariable UUID lineId,
            @Valid @RequestBody UpdateBomLineRequest request) {

        var orgId = JwtClaimsExtractor.orgId(jwt);
        return BomMapper.toLineDto(bomService.updateLine(orgId, bomId, lineId, request));
    }

    @GetMapping("/{bomId}/explosion")
    @RequiresPrivilege("item-master:bom:manage")
    public List<BomExplosionNode> explode(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bomId,
            @RequestParam(defaultValue = "flat") String format,
            @RequestParam(required = false) LocalDate asOfDate,
            @RequestParam(required = false) String asOfUnit) {

        return explosionService.explode(JwtClaimsExtractor.orgId(jwt), bomId, format, asOfDate, asOfUnit);
    }
}
