package com.mes.workorder.itemmaster.api;

import com.mes.common.security.annotation.RequiresPrivilege;
import com.mes.workorder.itemmaster.api.dto.CreateItemMasterRequest;
import com.mes.workorder.itemmaster.api.dto.ItemMasterDto;
import com.mes.workorder.itemmaster.api.dto.ItemMasterMapper;
import com.mes.workorder.itemmaster.api.dto.PatchItemMasterRequest;
import com.mes.workorder.itemmaster.domain.Classification;
import com.mes.workorder.itemmaster.domain.ItemStatus;
import com.mes.workorder.itemmaster.service.ItemMasterService;
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
            @PageableDefault(size = 50) Pageable pageable) {

        UUID orgId = extractOrgId(jwt);
        var page = service.list(orgId, status, classification, pageable);
        var dtos = page.getContent().stream().map(ItemMasterMapper::toDto).toList();
        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    @PostMapping
    @RequiresPrivilege("item-master:records:manage")
    public ResponseEntity<ItemMasterDto> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateItemMasterRequest request) {

        UUID orgId = extractOrgId(jwt);
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

        return ItemMasterMapper.toDto(service.get(extractOrgId(jwt), itemId));
    }

    @PatchMapping("/{itemId}")
    @RequiresPrivilege("item-master:records:manage")
    public ItemMasterDto patch(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID itemId,
            @Valid @RequestBody PatchItemMasterRequest request) {

        return ItemMasterMapper.toDto(service.patch(extractOrgId(jwt), itemId, request));
    }

    @PostMapping("/{itemId}/obsolete")
    @RequiresPrivilege("item-master:records:manage")
    public ItemMasterDto obsolete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID itemId) {

        return ItemMasterMapper.toDto(service.obsolete(extractOrgId(jwt), itemId));
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
