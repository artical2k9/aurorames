package com.mes.inventory.bom.api;

import com.mes.common.security.annotation.RequiresPrivilege;
import com.mes.udf.api.JwtClaimsExtractor;
import com.mes.inventory.bom.api.dto.BomDto;
import com.mes.inventory.bom.api.dto.BomExplosionNode;
import com.mes.inventory.bom.api.dto.BomLineDto;
import com.mes.inventory.bom.api.dto.BomRevisionSummaryDto;
import com.mes.inventory.bom.api.dto.BomSummaryDto;
import com.mes.inventory.bom.api.dto.CreateBomLineRequest;
import com.mes.inventory.bom.api.dto.CreateBomRequest;
import com.mes.inventory.bom.api.dto.PatchBomHeaderRequest;
import com.mes.inventory.bom.api.dto.UpdateBomLineRequest;
import com.mes.inventory.bom.service.BomExplosionService;
import com.mes.inventory.bom.service.BomExportService;
import com.mes.inventory.bom.service.BomService;
import com.mes.inventory.itemmaster.api.dto.RejectRevisionRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
    private final BomExportService exportService;

    public BomController(BomService bomService, BomExplosionService explosionService,
                         BomExportService exportService) {
        this.bomService = bomService;
        this.explosionService = explosionService;
        this.exportService = exportService;
    }

    @PostMapping
    @RequiresPrivilege("item-master:bom:manage")
    public ResponseEntity<BomDto> createBom(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateBomRequest request) {

        var orgId = JwtClaimsExtractor.orgId(jwt);
        var dto = bomService.createBom(orgId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(dto.getId()).toUri();
        return ResponseEntity.created(location).body(dto);
    }

    @GetMapping("/{bomId}")
    @RequiresPrivilege("item-master:bom:manage")
    public BomDto getBom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bomId) {

        return bomService.getBom(JwtClaimsExtractor.orgId(jwt), bomId);
    }

    @PostMapping("/{bomId}/submit")
    @RequiresPrivilege("item-master:bom:manage")
    public BomDto submitDraft(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bomId) {

        return bomService.submitDraft(JwtClaimsExtractor.orgId(jwt), bomId,
                JwtClaimsExtractor.nullSafeSubject(jwt));
    }

    @PostMapping("/{bomId}/approve")
    @RequiresPrivilege("bom:revisions:approve")
    public BomDto approveDraft(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bomId) {

        return bomService.approveDraft(JwtClaimsExtractor.orgId(jwt), bomId,
                JwtClaimsExtractor.nullSafeSubject(jwt));
    }

    @PostMapping("/{bomId}/reject")
    @RequiresPrivilege("bom:revisions:approve")
    public BomDto rejectDraft(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bomId,
            @Valid @RequestBody RejectRevisionRequest request) {

        return bomService.rejectDraft(JwtClaimsExtractor.orgId(jwt), bomId,
                JwtClaimsExtractor.nullSafeSubject(jwt), request.getRejectionReason());
    }

    @DeleteMapping("/{bomId}/draft")
    @RequiresPrivilege("item-master:bom:manage")
    public ResponseEntity<Void> cancelDraft(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bomId) {

        bomService.cancelDraft(JwtClaimsExtractor.orgId(jwt), bomId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{bomId}/lines")
    @RequiresPrivilege("item-master:bom:manage")
    public List<BomLineDto> listLines(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bomId) {

        return bomService.listEnrichedLines(JwtClaimsExtractor.orgId(jwt), bomId);
    }

    @PostMapping("/{bomId}/lines")
    @RequiresPrivilege("item-master:bom:manage")
    public ResponseEntity<BomLineDto> addLine(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bomId,
            @Valid @RequestBody CreateBomLineRequest request) {

        var orgId = JwtClaimsExtractor.orgId(jwt);
        var dto = bomService.addLine(orgId, bomId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(dto.getId()).toUri();
        return ResponseEntity.created(location).body(dto);
    }

    @PatchMapping("/{bomId}/lines/{lineId}")
    @RequiresPrivilege("item-master:bom:manage")
    public BomLineDto updateLine(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bomId,
            @PathVariable UUID lineId,
            @Valid @RequestBody UpdateBomLineRequest request) {

        var orgId = JwtClaimsExtractor.orgId(jwt);
        return bomService.updateLine(orgId, bomId, lineId, request);
    }

    @GetMapping("/headers")
    @RequiresPrivilege("item-master:bom:manage")
    public Page<BomSummaryDto> listHeaders(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        var pageable = PageRequest.of(page, size);
        return bomService.listSummaries(JwtClaimsExtractor.orgId(jwt), search, pageable);
    }

    @DeleteMapping("/{bomId}/lines/{lineId}")
    @RequiresPrivilege("item-master:bom:manage")
    public ResponseEntity<Void> deleteLine(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bomId,
            @PathVariable UUID lineId) {

        bomService.deleteLine(JwtClaimsExtractor.orgId(jwt), bomId, lineId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{bomId}")
    @RequiresPrivilege("item-master:bom:manage")
    public BomDto patchHeader(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bomId,
            @Valid @RequestBody PatchBomHeaderRequest request) {

        return bomService.patchHeader(JwtClaimsExtractor.orgId(jwt), bomId, request);
    }

    @GetMapping("/{bomId}/explosion")
    @RequiresPrivilege("item-master:bom:manage")
    public List<BomExplosionNode> explode(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bomId,
            @RequestParam(defaultValue = "flat") String format,
            @RequestParam(required = false) LocalDate asOfDate,
            @RequestParam(required = false) String asOfUnit,
            @RequestParam(required = false, defaultValue = "0") int maxDepth) {

        return explosionService.explode(JwtClaimsExtractor.orgId(jwt), bomId, format,
                asOfDate, asOfUnit, maxDepth);
    }

    /** T086 — Revision history for a BOM, ordered by revision ASC. */
    @GetMapping("/{bomId}/revisions")
    @RequiresPrivilege("item-master:bom:manage")
    public List<BomRevisionSummaryDto> listRevisions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bomId) {

        return bomService.listRevisions(JwtClaimsExtractor.orgId(jwt), bomId);
    }

    @GetMapping("/{bomId}/explosion/download")
    @RequiresPrivilege("item-master:records:view")
    public ResponseEntity<byte[]> downloadExplosion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bomId,
            @RequestParam(defaultValue = "flat") String format,
            @RequestParam String download,
            @RequestParam(required = false) LocalDate asOfDate,
            @RequestParam(required = false) String asOfUnit,
            @RequestParam(required = false, defaultValue = "0") int maxDepth) {

        var orgId = JwtClaimsExtractor.orgId(jwt);
        var nodes = explosionService.explode(orgId, bomId, format, asOfDate, asOfUnit, maxDepth);
        var dto = bomService.getBom(orgId, bomId);

        if ("pdf".equalsIgnoreCase(download)) {
            byte[] pdf = exportService.toPdfFromDto(dto, nodes);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"bom-" + bomId + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        }
        byte[] csv = exportService.toCsv(nodes);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"bom-" + bomId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}
