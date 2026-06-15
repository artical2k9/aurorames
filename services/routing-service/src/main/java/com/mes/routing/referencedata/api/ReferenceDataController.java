package com.mes.routing.referencedata.api;

import com.mes.common.security.annotation.RequiresPrivilege;
import com.mes.routing.referencedata.api.dto.ReferenceDataDtos.LabourCodeDto;
import com.mes.routing.referencedata.api.dto.ReferenceDataDtos.LabourPlanTypeDto;
import com.mes.routing.referencedata.api.dto.ReferenceDataDtos.RouteTypeDto;
import com.mes.routing.referencedata.api.dto.ReferenceDataDtos.SignificantProcessTypeDto;
import com.mes.routing.referencedata.api.dto.ReferenceDataDtos.SupplierDto;
import com.mes.routing.referencedata.api.dto.ReferenceDataDtos.WorkCentreDto;
import com.mes.routing.referencedata.service.ReferenceDataService;
import com.mes.udf.api.JwtClaimsExtractor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Routing reference-data Settings submodule (US10).
 * Reads require {@code routing:settings:view}; writes require {@code routing:settings:manage}.
 */
@RestController
@RequestMapping("/api/v1/routing")
public class ReferenceDataController {

    private final ReferenceDataService service;

    public ReferenceDataController(ReferenceDataService service) {
        this.service = service;
    }

    private static UUID org(Jwt jwt) {
        return JwtClaimsExtractor.orgId(jwt);
    }

    // ── Work centres ─────────────────────────────────────────────────────────
    @GetMapping("/work-centres")
    @RequiresPrivilege("routing:settings:view")
    public List<WorkCentreDto> listWorkCentres(@AuthenticationPrincipal Jwt jwt) {
        return service.listWorkCentres(org(jwt));
    }

    @PostMapping("/work-centres")
    @RequiresPrivilege("routing:settings:manage")
    public ResponseEntity<WorkCentreDto> createWorkCentre(@AuthenticationPrincipal Jwt jwt,
                                                          @Valid @RequestBody WorkCentreDto req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createWorkCentre(org(jwt), req));
    }

    @PatchMapping("/work-centres/{id}")
    @RequiresPrivilege("routing:settings:manage")
    public WorkCentreDto updateWorkCentre(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                          @Valid @RequestBody WorkCentreDto req) {
        return service.updateWorkCentre(org(jwt), id, req);
    }

    @DeleteMapping("/work-centres/{id}")
    @RequiresPrivilege("routing:settings:manage")
    public ResponseEntity<Void> deleteWorkCentre(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.deleteWorkCentre(org(jwt), id);
        return ResponseEntity.noContent().build();
    }

    // ── Labour plan types ──────────────────────────────────────────────────────
    @GetMapping("/labour-plan-types")
    @RequiresPrivilege("routing:settings:view")
    public List<LabourPlanTypeDto> listLabourPlanTypes(@AuthenticationPrincipal Jwt jwt) {
        return service.listLabourPlanTypes(org(jwt));
    }

    @PostMapping("/labour-plan-types")
    @RequiresPrivilege("routing:settings:manage")
    public ResponseEntity<LabourPlanTypeDto> createLabourPlanType(@AuthenticationPrincipal Jwt jwt,
                                                                  @Valid @RequestBody LabourPlanTypeDto req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createLabourPlanType(org(jwt), req));
    }

    @PatchMapping("/labour-plan-types/{id}")
    @RequiresPrivilege("routing:settings:manage")
    public LabourPlanTypeDto updateLabourPlanType(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                  @Valid @RequestBody LabourPlanTypeDto req) {
        return service.updateLabourPlanType(org(jwt), id, req);
    }

    @DeleteMapping("/labour-plan-types/{id}")
    @RequiresPrivilege("routing:settings:manage")
    public ResponseEntity<Void> deleteLabourPlanType(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.deleteLabourPlanType(org(jwt), id);
        return ResponseEntity.noContent().build();
    }

    // ── Labour codes ───────────────────────────────────────────────────────────
    @GetMapping("/labour-codes")
    @RequiresPrivilege("routing:settings:view")
    public List<LabourCodeDto> listLabourCodes(@AuthenticationPrincipal Jwt jwt) {
        return service.listLabourCodes(org(jwt));
    }

    @PostMapping("/labour-codes")
    @RequiresPrivilege("routing:settings:manage")
    public ResponseEntity<LabourCodeDto> createLabourCode(@AuthenticationPrincipal Jwt jwt,
                                                          @Valid @RequestBody LabourCodeDto req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createLabourCode(org(jwt), req));
    }

    @PatchMapping("/labour-codes/{id}")
    @RequiresPrivilege("routing:settings:manage")
    public LabourCodeDto updateLabourCode(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                          @Valid @RequestBody LabourCodeDto req) {
        return service.updateLabourCode(org(jwt), id, req);
    }

    @DeleteMapping("/labour-codes/{id}")
    @RequiresPrivilege("routing:settings:manage")
    public ResponseEntity<Void> deleteLabourCode(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.deleteLabourCode(org(jwt), id);
        return ResponseEntity.noContent().build();
    }

    // ── Route types ────────────────────────────────────────────────────────────
    @GetMapping("/route-types")
    @RequiresPrivilege("routing:settings:view")
    public List<RouteTypeDto> listRouteTypes(@AuthenticationPrincipal Jwt jwt) {
        return service.listRouteTypes(org(jwt));
    }

    @PostMapping("/route-types")
    @RequiresPrivilege("routing:settings:manage")
    public ResponseEntity<RouteTypeDto> createRouteType(@AuthenticationPrincipal Jwt jwt,
                                                        @Valid @RequestBody RouteTypeDto req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createRouteType(org(jwt), req));
    }

    @PatchMapping("/route-types/{id}")
    @RequiresPrivilege("routing:settings:manage")
    public RouteTypeDto updateRouteType(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                        @Valid @RequestBody RouteTypeDto req) {
        return service.updateRouteType(org(jwt), id, req);
    }

    @DeleteMapping("/route-types/{id}")
    @RequiresPrivilege("routing:settings:manage")
    public ResponseEntity<Void> deleteRouteType(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.deleteRouteType(org(jwt), id);
        return ResponseEntity.noContent().build();
    }

    // ── Significant-process types ────────────────────────────────────────────────
    @GetMapping("/significant-process-types")
    @RequiresPrivilege("routing:settings:view")
    public List<SignificantProcessTypeDto> listSignificantProcessTypes(@AuthenticationPrincipal Jwt jwt) {
        return service.listSignificantProcessTypes(org(jwt));
    }

    @PostMapping("/significant-process-types")
    @RequiresPrivilege("routing:settings:manage")
    public ResponseEntity<SignificantProcessTypeDto> createSignificantProcessType(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SignificantProcessTypeDto req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createSignificantProcessType(org(jwt), req));
    }

    @PatchMapping("/significant-process-types/{id}")
    @RequiresPrivilege("routing:settings:manage")
    public SignificantProcessTypeDto updateSignificantProcessType(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody SignificantProcessTypeDto req) {
        return service.updateSignificantProcessType(org(jwt), id, req);
    }

    @DeleteMapping("/significant-process-types/{id}")
    @RequiresPrivilege("routing:settings:manage")
    public ResponseEntity<Void> deleteSignificantProcessType(@AuthenticationPrincipal Jwt jwt,
                                                             @PathVariable UUID id) {
        service.deleteSignificantProcessType(org(jwt), id);
        return ResponseEntity.noContent().build();
    }

    // ── Suppliers ────────────────────────────────────────────────────────────────
    @GetMapping("/suppliers")
    @RequiresPrivilege("routing:settings:view")
    public List<SupplierDto> listSuppliers(@AuthenticationPrincipal Jwt jwt) {
        return service.listSuppliers(org(jwt));
    }

    @PostMapping("/suppliers")
    @RequiresPrivilege("routing:settings:manage")
    public ResponseEntity<SupplierDto> createSupplier(@AuthenticationPrincipal Jwt jwt,
                                                      @Valid @RequestBody SupplierDto req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createSupplier(org(jwt), req));
    }

    @PatchMapping("/suppliers/{id}")
    @RequiresPrivilege("routing:settings:manage")
    public SupplierDto updateSupplier(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                      @Valid @RequestBody SupplierDto req) {
        return service.updateSupplier(org(jwt), id, req);
    }

    @DeleteMapping("/suppliers/{id}")
    @RequiresPrivilege("routing:settings:manage")
    public ResponseEntity<Void> deleteSupplier(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.deleteSupplier(org(jwt), id);
        return ResponseEntity.noContent().build();
    }
}
