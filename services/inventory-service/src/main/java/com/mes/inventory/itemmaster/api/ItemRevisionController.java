package com.mes.inventory.itemmaster.api;

import com.mes.common.security.annotation.RequiresPrivilege;
import com.mes.udf.api.JwtClaimsExtractor;
import com.mes.inventory.itemmaster.api.dto.CreateItemMasterRequest;
import com.mes.inventory.itemmaster.api.dto.ItemMasterDto;
import com.mes.inventory.itemmaster.api.dto.ItemRevisionSummaryDto;
import com.mes.inventory.itemmaster.api.dto.PatchAs5553Request;
import com.mes.inventory.itemmaster.api.dto.PatchItemMasterRequest;
import com.mes.inventory.itemmaster.api.dto.RejectRevisionRequest;
import com.mes.inventory.itemmaster.domain.RevisionStatus;
import com.mes.inventory.itemmaster.service.ItemRevisionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NO_CONTENT;

@RestController
@RequestMapping("/api/v1/item-master")
public class ItemRevisionController {

    private final ItemRevisionService service;

    public ItemRevisionController(ItemRevisionService service) {
        this.service = service;
    }

    /** T023 — List items (one per identity, showing display revision). */
    @GetMapping
    @RequiresPrivilege("item-master:records:view")
    public Page<ItemMasterDto> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) RevisionStatus revisionStatus,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String makeBuyCode,
            @RequestParam(required = false) String counterfeitRiskLevel,
            @PageableDefault(size = 50) Pageable pageable) {

        return service.listItems(JwtClaimsExtractor.orgId(jwt), revisionStatus, search,
                makeBuyCode, counterfeitRiskLevel, pageable);
    }

    /** T024 — Create item + initial DRAFT revision. */
    @PostMapping
    @RequiresPrivilege("item-master:records:manage")
    public ResponseEntity<ItemMasterDto> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateItemMasterRequest request) {

        var orgId = JwtClaimsExtractor.orgId(jwt);
        var dto = service.createItem(orgId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(dto.getId()).toUri();
        return ResponseEntity.created(location).body(dto);
    }

    /** T025 — Get item by identity UUID. Optional revisionStatus selects which revision to show. */
    @GetMapping("/{itemId}")
    @RequiresPrivilege("item-master:records:view")
    public ItemMasterDto get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID itemId,
            @RequestParam(required = false) RevisionStatus revisionStatus) {

        return service.getItem(JwtClaimsExtractor.orgId(jwt), itemId, revisionStatus);
    }

    /** T026 — Patch item (auto-creates DRAFT if currently APPROVED). */
    @PatchMapping("/{itemId}")
    @RequiresPrivilege("item-master:records:manage")
    public ItemMasterDto patch(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID itemId,
            @Valid @RequestBody PatchItemMasterRequest request) {

        var actor = JwtClaimsExtractor.nullSafeSubject(jwt);
        return service.patchItem(JwtClaimsExtractor.orgId(jwt), itemId, request, actor);
    }

    /** AS5553 patch — routes through patchItem with AS5553-only fields. */
    @PatchMapping("/{itemId}/as5553")
    @RequiresPrivilege("item-master:as5553:manage")
    public ItemMasterDto patchAs5553(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID itemId,
            @RequestBody PatchAs5553Request request) {

        PatchItemMasterRequest patch = new PatchItemMasterRequest();
        patch.setCounterfeitRiskLevel(request.getCounterfeitRiskLevel());
        patch.setApprovedSuppliers(request.getApprovedSuppliers());
        patch.setVerificationRequired(request.getVerificationRequired());
        var actor = JwtClaimsExtractor.nullSafeSubject(jwt);
        return service.patchItem(JwtClaimsExtractor.orgId(jwt), itemId, patch, actor);
    }

    /** T027 — Submit DRAFT for approval. */
    @PostMapping("/{itemId}/submit")
    @RequiresPrivilege("item-master:records:manage")
    public ItemMasterDto submit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID itemId) {

        var actor = JwtClaimsExtractor.nullSafeSubject(jwt);
        return service.submitDraft(JwtClaimsExtractor.orgId(jwt), itemId, actor);
    }

    /** T027 — Approve PENDING_APPROVAL revision. */
    @PostMapping("/{itemId}/approve")
    @RequiresPrivilege("item-master:revisions:approve")
    public ItemMasterDto approve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID itemId) {

        var actor = JwtClaimsExtractor.nullSafeSubject(jwt);
        return service.approveDraft(JwtClaimsExtractor.orgId(jwt), itemId, actor);
    }

    /** T027a — Reject PENDING_APPROVAL revision (with required reason). */
    @PostMapping("/{itemId}/reject")
    @RequiresPrivilege("item-master:revisions:approve")
    public ItemMasterDto reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID itemId,
            @Valid @RequestBody RejectRevisionRequest request) {

        var actor = JwtClaimsExtractor.nullSafeSubject(jwt);
        return service.rejectDraft(JwtClaimsExtractor.orgId(jwt), itemId, actor,
                request.getRejectionReason());
    }

    /** T028 — Hard-delete the current DRAFT revision. */
    @DeleteMapping("/{itemId}/draft")
    @RequiresPrivilege("item-master:records:manage")
    @ResponseStatus(NO_CONTENT)
    public void cancelDraft(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID itemId) {

        service.cancelDraft(JwtClaimsExtractor.orgId(jwt), itemId);
    }

    /** T084 — Revision history for an item, ordered by revision ASC. */
    @GetMapping("/{itemId}/revisions")
    @RequiresPrivilege("item-master:records:view")
    public List<ItemRevisionSummaryDto> listRevisions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID itemId) {

        return service.listRevisions(JwtClaimsExtractor.orgId(jwt), itemId);
    }

    /** T036a — Clone from APPROVED source (422 if no APPROVED exists). */
    @PostMapping("/{itemId}/clone")
    @RequiresPrivilege("item-master:records:manage")
    public ItemMasterDto clone(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID itemId) {

        return service.cloneAsTemplate(JwtClaimsExtractor.orgId(jwt), itemId);
    }
}
