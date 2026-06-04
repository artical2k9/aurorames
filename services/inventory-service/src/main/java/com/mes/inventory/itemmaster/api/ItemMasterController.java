package com.mes.inventory.itemmaster.api;

import com.mes.common.security.annotation.RequiresPrivilege;
import com.mes.udf.api.JwtClaimsExtractor;
import com.mes.inventory.itemmaster.api.dto.CreateItemMasterRequest;
import com.mes.inventory.itemmaster.api.dto.ItemMasterDto;
import com.mes.inventory.itemmaster.api.dto.ItemMasterMapper;
import com.mes.inventory.itemmaster.api.dto.PatchItemMasterRequest;
import com.mes.inventory.itemmaster.domain.Classification;
import com.mes.inventory.itemmaster.domain.CounterfeitRiskLevel;
import com.mes.inventory.itemmaster.domain.ItemStatus;
import com.mes.inventory.itemmaster.service.ItemMasterService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
@RequestMapping("/api/v1/item-master")
public class ItemMasterController {

    private final ItemMasterService service;

    public ItemMasterController(ItemMasterService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPrivilege("item-master:records:view")
    public Page<ItemMasterDto> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) ItemStatus status,
            @RequestParam(required = false) Classification classification,
            @RequestParam(required = false) CounterfeitRiskLevel counterfeitRiskLevel,
            @PageableDefault(size = 50) Pageable pageable) {

        var orgId = JwtClaimsExtractor.orgId(jwt);
        var page = service.list(orgId, status, classification, counterfeitRiskLevel, pageable);
        var dtos = page.getContent().stream().map(ItemMasterMapper::toDto).toList();
        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    @PostMapping
    @RequiresPrivilege("item-master:records:manage")
    public ResponseEntity<ItemMasterDto> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateItemMasterRequest request) {

        var orgId = JwtClaimsExtractor.orgId(jwt);
        var entity = service.create(orgId, request.getPartNumber(), request.getRevision(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(entity.getId()).toUri();
        return ResponseEntity.created(location).body(ItemMasterMapper.toDto(entity));
    }

    @GetMapping("/{itemId}")
    @RequiresPrivilege("item-master:records:view")
    public ItemMasterDto get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID itemId) {

        return ItemMasterMapper.toDto(service.get(JwtClaimsExtractor.orgId(jwt), itemId));
    }

    @PatchMapping("/{itemId}")
    @RequiresPrivilege("item-master:records:manage")
    public ItemMasterDto patch(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID itemId,
            @Valid @RequestBody PatchItemMasterRequest request) {

        return ItemMasterMapper.toDto(service.patch(JwtClaimsExtractor.orgId(jwt), itemId, request));
    }

    @PostMapping("/{itemId}/obsolete")
    @RequiresPrivilege("item-master:records:manage")
    public ItemMasterDto obsolete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID itemId) {

        return ItemMasterMapper.toDto(service.obsolete(JwtClaimsExtractor.orgId(jwt), itemId));
    }
}
