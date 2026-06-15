package com.mes.routing.referencedata.service;

import com.mes.routing.referencedata.api.dto.ReferenceDataDtos.LabourCodeDto;
import com.mes.routing.referencedata.api.dto.ReferenceDataDtos.LabourPlanTypeDto;
import com.mes.routing.referencedata.api.dto.ReferenceDataDtos.RouteTypeDto;
import com.mes.routing.referencedata.api.dto.ReferenceDataDtos.SignificantProcessTypeDto;
import com.mes.routing.referencedata.api.dto.ReferenceDataDtos.SupplierDto;
import com.mes.routing.referencedata.api.dto.ReferenceDataDtos.WorkCentreDto;
import com.mes.routing.referencedata.domain.LabourCode;
import com.mes.routing.referencedata.domain.LabourPlanType;
import com.mes.routing.referencedata.domain.RouteType;
import com.mes.routing.referencedata.domain.SignificantProcessType;
import com.mes.routing.referencedata.domain.Supplier;
import com.mes.routing.referencedata.domain.WorkCentre;
import com.mes.routing.referencedata.repository.LabourCodeRepository;
import com.mes.routing.referencedata.repository.LabourPlanTypeRepository;
import com.mes.routing.referencedata.repository.RouteTypeRepository;
import com.mes.routing.referencedata.repository.SignificantProcessTypeRepository;
import com.mes.routing.referencedata.repository.SupplierRepository;
import com.mes.routing.referencedata.repository.WorkCentreRepository;
import com.mes.routing.service.RoutingConflictException;
import com.mes.routing.service.RoutingNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * CRUD for the routing reference-data Settings submodule (US10). All operations are
 * organisation-scoped (§IX). Seeded protected entries cannot be deleted; entries in use
 * cannot be deleted (deactivate instead). Code is unique per organisation.
 */
@Service
@Transactional
public class ReferenceDataService {

    private final WorkCentreRepository workCentres;
    private final LabourPlanTypeRepository labourPlanTypes;
    private final LabourCodeRepository labourCodes;
    private final RouteTypeRepository routeTypes;
    private final SignificantProcessTypeRepository significantProcessTypes;
    private final SupplierRepository suppliers;

    public ReferenceDataService(WorkCentreRepository workCentres,
                                LabourPlanTypeRepository labourPlanTypes,
                                LabourCodeRepository labourCodes,
                                RouteTypeRepository routeTypes,
                                SignificantProcessTypeRepository significantProcessTypes,
                                SupplierRepository suppliers) {
        this.workCentres = workCentres;
        this.labourPlanTypes = labourPlanTypes;
        this.labourCodes = labourCodes;
        this.routeTypes = routeTypes;
        this.significantProcessTypes = significantProcessTypes;
        this.suppliers = suppliers;
    }

    private void requireUniqueCode(boolean exists, String code) {
        if (exists) {
            throw new RoutingConflictException("Code already exists: " + code);
        }
    }

    // ── Work centres ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<WorkCentreDto> listWorkCentres(UUID orgId) {
        return workCentres.findByOrgIdOrderByCode(orgId).stream().map(ReferenceDataService::toDto).toList();
    }

    public WorkCentreDto createWorkCentre(UUID orgId, WorkCentreDto req) {
        requireUniqueCode(workCentres.existsByOrgIdAndCode(orgId, req.code()), req.code());
        WorkCentre e = new WorkCentre();
        e.setOrgId(orgId);
        e.setCode(req.code());
        e.setName(req.name());
        e.setDescription(req.description());
        e.setActive(true);
        return toDto(workCentres.save(e));
    }

    public WorkCentreDto updateWorkCentre(UUID orgId, UUID id, WorkCentreDto req) {
        WorkCentre e = workCentres.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> notFound("Work centre", id));
        e.setName(req.name());
        e.setDescription(req.description());
        e.setActive(req.active());
        return toDto(workCentres.save(e));
    }

    public void deleteWorkCentre(UUID orgId, UUID id) {
        WorkCentre e = workCentres.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> notFound("Work centre", id));
        workCentres.delete(e);
    }

    // ── Labour plan types ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<LabourPlanTypeDto> listLabourPlanTypes(UUID orgId) {
        return labourPlanTypes.findByOrgIdOrderByCode(orgId).stream().map(ReferenceDataService::toDto).toList();
    }

    public LabourPlanTypeDto createLabourPlanType(UUID orgId, LabourPlanTypeDto req) {
        requireUniqueCode(labourPlanTypes.existsByOrgIdAndCode(orgId, req.code()), req.code());
        LabourPlanType e = new LabourPlanType();
        e.setOrgId(orgId);
        e.setCode(req.code());
        e.setName(req.name());
        e.setSeeded(false);
        e.setActive(true);
        return toDto(labourPlanTypes.save(e));
    }

    public LabourPlanTypeDto updateLabourPlanType(UUID orgId, UUID id, LabourPlanTypeDto req) {
        LabourPlanType e = labourPlanTypes.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> notFound("Labour plan type", id));
        e.setName(req.name());
        e.setActive(req.active());
        return toDto(labourPlanTypes.save(e));
    }

    public void deleteLabourPlanType(UUID orgId, UUID id) {
        LabourPlanType e = labourPlanTypes.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> notFound("Labour plan type", id));
        if (e.isSeeded()) {
            throw new RoutingConflictException("Seeded labour plan type cannot be deleted: " + e.getCode());
        }
        if (labourCodes.existsByOrgIdAndLabourPlanTypeId(orgId, id)) {
            throw new RoutingConflictException("Labour plan type is in use by a labour code; deactivate instead");
        }
        labourPlanTypes.delete(e);
    }

    // ── Labour codes ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<LabourCodeDto> listLabourCodes(UUID orgId) {
        return labourCodes.findByOrgIdOrderByCode(orgId).stream().map(ReferenceDataService::toDto).toList();
    }

    public LabourCodeDto createLabourCode(UUID orgId, LabourCodeDto req) {
        requireUniqueCode(labourCodes.existsByOrgIdAndCode(orgId, req.code()), req.code());
        if (req.labourPlanTypeId() != null
                && labourPlanTypes.findByOrgIdAndId(orgId, req.labourPlanTypeId()).isEmpty()) {
            throw notFound("Labour plan type", req.labourPlanTypeId());
        }
        LabourCode e = new LabourCode();
        e.setOrgId(orgId);
        e.setCode(req.code());
        e.setName(req.name());
        e.setLabourPlanTypeId(req.labourPlanTypeId());
        e.setActive(true);
        return toDto(labourCodes.save(e));
    }

    public LabourCodeDto updateLabourCode(UUID orgId, UUID id, LabourCodeDto req) {
        LabourCode e = labourCodes.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> notFound("Labour code", id));
        e.setName(req.name());
        e.setLabourPlanTypeId(req.labourPlanTypeId());
        e.setActive(req.active());
        return toDto(labourCodes.save(e));
    }

    public void deleteLabourCode(UUID orgId, UUID id) {
        LabourCode e = labourCodes.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> notFound("Labour code", id));
        labourCodes.delete(e);
    }

    // ── Route types ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RouteTypeDto> listRouteTypes(UUID orgId) {
        return routeTypes.findByOrgIdOrderByCode(orgId).stream().map(ReferenceDataService::toDto).toList();
    }

    public RouteTypeDto createRouteType(UUID orgId, RouteTypeDto req) {
        requireUniqueCode(routeTypes.existsByOrgIdAndCode(orgId, req.code()), req.code());
        RouteType e = new RouteType();
        e.setOrgId(orgId);
        e.setCode(req.code());
        e.setName(req.name());
        e.setStandard(false); // Standard is a seeded protected default; alternates are never Standard.
        e.setSeeded(false);
        e.setActive(true);
        return toDto(routeTypes.save(e));
    }

    public RouteTypeDto updateRouteType(UUID orgId, UUID id, RouteTypeDto req) {
        RouteType e = routeTypes.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> notFound("Route type", id));
        e.setName(req.name());
        e.setActive(req.active());
        return toDto(routeTypes.save(e));
    }

    public void deleteRouteType(UUID orgId, UUID id) {
        RouteType e = routeTypes.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> notFound("Route type", id));
        if (e.isStandard() || e.isSeeded()) {
            throw new RoutingConflictException("Seeded/Standard route type cannot be deleted: " + e.getCode());
        }
        // In-use-by-routes check is added when the route aggregate exists (PR 2).
        routeTypes.delete(e);
    }

    // ── Significant-process types ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SignificantProcessTypeDto> listSignificantProcessTypes(UUID orgId) {
        return significantProcessTypes.findByOrgIdOrderByCode(orgId).stream()
                .map(ReferenceDataService::toDto).toList();
    }

    public SignificantProcessTypeDto createSignificantProcessType(UUID orgId, SignificantProcessTypeDto req) {
        requireUniqueCode(significantProcessTypes.existsByOrgIdAndCode(orgId, req.code()), req.code());
        SignificantProcessType e = new SignificantProcessType();
        e.setOrgId(orgId);
        e.setCode(req.code());
        e.setName(req.name());
        e.setRequiredApproverRole(req.requiredApproverRole());
        e.setActive(true);
        return toDto(significantProcessTypes.save(e));
    }

    public SignificantProcessTypeDto updateSignificantProcessType(UUID orgId, UUID id,
                                                                  SignificantProcessTypeDto req) {
        SignificantProcessType e = significantProcessTypes.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> notFound("Significant process type", id));
        e.setName(req.name());
        e.setRequiredApproverRole(req.requiredApproverRole());
        e.setActive(req.active());
        return toDto(significantProcessTypes.save(e));
    }

    public void deleteSignificantProcessType(UUID orgId, UUID id) {
        SignificantProcessType e = significantProcessTypes.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> notFound("Significant process type", id));
        significantProcessTypes.delete(e);
    }

    // ── Suppliers ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SupplierDto> listSuppliers(UUID orgId) {
        return suppliers.findByOrgIdOrderByCode(orgId).stream().map(ReferenceDataService::toDto).toList();
    }

    public SupplierDto createSupplier(UUID orgId, SupplierDto req) {
        requireUniqueCode(suppliers.existsByOrgIdAndCode(orgId, req.code()), req.code());
        Supplier e = new Supplier();
        e.setOrgId(orgId);
        e.setCode(req.code());
        e.setName(req.name());
        e.setActive(true);
        return toDto(suppliers.save(e));
    }

    public SupplierDto updateSupplier(UUID orgId, UUID id, SupplierDto req) {
        Supplier e = suppliers.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> notFound("Supplier", id));
        e.setName(req.name());
        e.setActive(req.active());
        return toDto(suppliers.save(e));
    }

    public void deleteSupplier(UUID orgId, UUID id) {
        Supplier e = suppliers.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> notFound("Supplier", id));
        suppliers.delete(e);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private static RoutingNotFoundException notFound(String label, UUID id) {
        return new RoutingNotFoundException(label + " not found: " + id);
    }

    private static WorkCentreDto toDto(WorkCentre e) {
        return new WorkCentreDto(e.getId(), e.getCode(), e.getName(), e.getDescription(), e.isActive());
    }

    private static LabourPlanTypeDto toDto(LabourPlanType e) {
        return new LabourPlanTypeDto(e.getId(), e.getCode(), e.getName(), e.isSeeded(), e.isActive());
    }

    private static LabourCodeDto toDto(LabourCode e) {
        return new LabourCodeDto(e.getId(), e.getCode(), e.getName(), e.getLabourPlanTypeId(), e.isActive());
    }

    private static RouteTypeDto toDto(RouteType e) {
        return new RouteTypeDto(e.getId(), e.getCode(), e.getName(), e.isStandard(), e.isSeeded(), e.isActive());
    }

    private static SignificantProcessTypeDto toDto(SignificantProcessType e) {
        return new SignificantProcessTypeDto(e.getId(), e.getCode(), e.getName(),
                e.getRequiredApproverRole(), e.isActive());
    }

    private static SupplierDto toDto(Supplier e) {
        return new SupplierDto(e.getId(), e.getCode(), e.getName(), e.isActive());
    }
}
