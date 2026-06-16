package com.mes.routing.route.service;

import com.mes.routing.referencedata.repository.LabourCodeRepository;
import com.mes.routing.referencedata.repository.LabourPlanTypeRepository;
import com.mes.routing.referencedata.repository.WorkCentreRepository;
import com.mes.routing.route.api.dto.OperationDetailDtos.LabourPlanLineDto;
import com.mes.routing.route.api.dto.OperationDetailDtos.MaterialConsumptionDto;
import com.mes.routing.route.api.dto.OperationDetailDtos.OperationResourceDto;
import com.mes.routing.route.api.dto.OperationDetailDtos.QualityVariableDto;
import com.mes.routing.route.api.dto.OperationDetailDtos.SkillRequirementDto;
import com.mes.routing.route.api.dto.OperationDetailDtos.StepFileReferenceDto;
import com.mes.routing.route.api.dto.OperationDetailDtos.ToolingRequirementDto;
import com.mes.routing.route.api.dto.OperationDetailDtos.WorkInstructionLinkDto;
import com.mes.routing.route.domain.LabourPlanLine;
import com.mes.routing.route.domain.MaterialConsumption;
import com.mes.routing.route.domain.OperationResource;
import com.mes.routing.route.domain.QualityVariableRequirement;
import com.mes.routing.route.domain.Route;
import com.mes.routing.route.domain.RouteStatus;
import com.mes.routing.route.domain.SkillRequirement;
import com.mes.routing.route.domain.StepFileReference;
import com.mes.routing.route.domain.ToolingRequirement;
import com.mes.routing.route.domain.WorkInstructionLink;
import com.mes.routing.route.repository.LabourPlanLineRepository;
import com.mes.routing.route.repository.MaterialConsumptionRepository;
import com.mes.routing.route.repository.OperationResourceRepository;
import com.mes.routing.route.repository.QualityVariableRequirementRepository;
import com.mes.routing.route.repository.SkillRequirementRepository;
import com.mes.routing.route.repository.StepFileReferenceRepository;
import com.mes.routing.route.repository.ToolingRequirementRepository;
import com.mes.routing.route.repository.WorkInstructionLinkRepository;
import com.mes.routing.route.repository.RouteOperationRepository;
import com.mes.routing.route.repository.RouteRepository;
import com.mes.routing.service.RoutingConflictException;
import com.mes.routing.service.RoutingNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Operation detail (US3): resources, labour standards, material consumption, quality variables,
 * tooling, skills, work-instruction link, STEP-file reference. Editable only while the route is
 * DRAFT. Settings-maintained references (work centre, labour plan type, labour code) are validated
 * locally; external references (BOM line, inspection characteristic, skill, work instruction) are
 * persisted as ids — cross-service validation is a documented follow-up.
 */
@Service
@Transactional
public class OperationDetailService {

    private final RouteRepository routes;
    private final RouteOperationRepository operations;
    private final WorkCentreRepository workCentres;
    private final LabourPlanTypeRepository labourPlanTypes;
    private final LabourCodeRepository labourCodes;
    private final OperationResourceRepository resources;
    private final LabourPlanLineRepository labourPlanLines;
    private final MaterialConsumptionRepository materials;
    private final QualityVariableRequirementRepository qualityVariables;
    private final ToolingRequirementRepository tooling;
    private final SkillRequirementRepository skills;
    private final WorkInstructionLinkRepository workInstructions;
    private final StepFileReferenceRepository stepFiles;
    private final RouteLockGuard lockGuard;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public OperationDetailService(RouteRepository routes, RouteOperationRepository operations,
                                  WorkCentreRepository workCentres, LabourPlanTypeRepository labourPlanTypes,
                                  LabourCodeRepository labourCodes,
                                  OperationResourceRepository resources,
                                  LabourPlanLineRepository labourPlanLines,
                                  MaterialConsumptionRepository materials,
                                  QualityVariableRequirementRepository qualityVariables,
                                  ToolingRequirementRepository tooling,
                                  SkillRequirementRepository skills,
                                  WorkInstructionLinkRepository workInstructions,
                                  StepFileReferenceRepository stepFiles,
                                  RouteLockGuard lockGuard) {
        this.routes = routes;
        this.operations = operations;
        this.workCentres = workCentres;
        this.labourPlanTypes = labourPlanTypes;
        this.labourCodes = labourCodes;
        this.resources = resources;
        this.labourPlanLines = labourPlanLines;
        this.materials = materials;
        this.qualityVariables = qualityVariables;
        this.tooling = tooling;
        this.skills = skills;
        this.workInstructions = workInstructions;
        this.stepFiles = stepFiles;
        this.lockGuard = lockGuard;
    }

    /** Resolves the operation, asserting the route is in-org and DRAFT; returns the owning org id. */
    private UUID requireDraftOperation(UUID orgId, UUID routeId, UUID opId) {
        Route route = routes.findByOrgIdAndId(orgId, routeId)
                .orElseThrow(() -> new RoutingNotFoundException("Route not found: " + routeId));
        if (route.getStatus() != RouteStatus.DRAFT) {
            throw new RoutingConflictException("Operation detail is editable only while DRAFT (status "
                    + route.getStatus() + ")");
        }
        lockGuard.requireHeld(route);
        operations.findByRouteIdAndId(routeId, opId)
                .orElseThrow(() -> new RoutingNotFoundException("Operation not found: " + opId));
        return route.getOrgId();
    }

    private void requireWorkCentre(UUID orgId, UUID id) {
        if (workCentres.findByOrgIdAndId(orgId, id).isEmpty()) {
            throw new RoutingNotFoundException("Work centre not found: " + id);
        }
    }

    // ── Resources ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<OperationResourceDto> listResources(UUID orgId, UUID routeId, UUID opId) {
        requireOperation(orgId, routeId, opId);
        return resources.findByOperationIdOrderByCreatedAt(opId).stream()
                .map(e -> new OperationResourceDto(e.getId(), e.getWorkCentreId())).toList();
    }

    public OperationResourceDto addResource(UUID orgId, UUID routeId, UUID opId, OperationResourceDto req) {
        UUID org = requireDraftOperation(orgId, routeId, opId);
        requireWorkCentre(orgId, req.workCentreId());
        OperationResource e = new OperationResource();
        e.setOrgId(org);
        e.setOperationId(opId);
        e.setWorkCentreId(req.workCentreId());
        OperationResource saved = resources.save(e);
        return new OperationResourceDto(saved.getId(), saved.getWorkCentreId());
    }

    public void deleteResource(UUID orgId, UUID routeId, UUID opId, UUID id) {
        requireDraftOperation(orgId, routeId, opId);
        resources.delete(resources.findByOperationIdAndId(opId, id)
                .orElseThrow(() -> notFound("Operation resource", id)));
    }

    // ── Labour plan lines ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<LabourPlanLineDto> listLabourPlan(UUID orgId, UUID routeId, UUID opId) {
        requireOperation(orgId, routeId, opId);
        return labourPlanLines.findByOperationIdOrderByCreatedAt(opId).stream()
                .map(e -> new LabourPlanLineDto(e.getId(), e.getLabourActivityType(),
                        e.getLabourPlanTypeId(), e.getLabourCodeId(), e.getTimeValue(), e.getBasis()))
                .toList();
    }

    public LabourPlanLineDto addLabourPlan(UUID orgId, UUID routeId, UUID opId, LabourPlanLineDto req) {
        UUID org = requireDraftOperation(orgId, routeId, opId);
        if (req.labourPlanTypeId() != null
                && labourPlanTypes.findByOrgIdAndId(orgId, req.labourPlanTypeId()).isEmpty()) {
            throw notFound("Labour plan type", req.labourPlanTypeId());
        }
        if (req.labourCodeId() != null && labourCodes.findByOrgIdAndId(orgId, req.labourCodeId()).isEmpty()) {
            throw notFound("Labour code", req.labourCodeId());
        }
        LabourPlanLine e = new LabourPlanLine();
        e.setOrgId(org);
        e.setOperationId(opId);
        e.setLabourActivityType(req.labourActivityType());
        e.setLabourPlanTypeId(req.labourPlanTypeId());
        e.setLabourCodeId(req.labourCodeId());
        e.setTimeValue(req.timeValue());
        e.setBasis(req.basis());
        LabourPlanLine saved = labourPlanLines.save(e);
        return new LabourPlanLineDto(saved.getId(), saved.getLabourActivityType(),
                saved.getLabourPlanTypeId(), saved.getLabourCodeId(), saved.getTimeValue(), saved.getBasis());
    }

    public void deleteLabourPlan(UUID orgId, UUID routeId, UUID opId, UUID id) {
        requireDraftOperation(orgId, routeId, opId);
        labourPlanLines.delete(labourPlanLines.findByOperationIdAndId(opId, id)
                .orElseThrow(() -> notFound("Labour plan line", id)));
    }

    // ── Material consumption ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MaterialConsumptionDto> listMaterials(UUID orgId, UUID routeId, UUID opId) {
        requireOperation(orgId, routeId, opId);
        return materials.findByOperationIdOrderByCreatedAt(opId).stream()
                .map(e -> new MaterialConsumptionDto(e.getId(), e.getBomLineId(), e.isMandatory())).toList();
    }

    public MaterialConsumptionDto addMaterial(UUID orgId, UUID routeId, UUID opId, MaterialConsumptionDto req) {
        UUID org = requireDraftOperation(orgId, routeId, opId);
        MaterialConsumption e = new MaterialConsumption();
        e.setOrgId(org);
        e.setOperationId(opId);
        e.setBomLineId(req.bomLineId());
        e.setMandatory(req.mandatory());
        MaterialConsumption saved = materials.save(e);
        return new MaterialConsumptionDto(saved.getId(), saved.getBomLineId(), saved.isMandatory());
    }

    public void deleteMaterial(UUID orgId, UUID routeId, UUID opId, UUID id) {
        requireDraftOperation(orgId, routeId, opId);
        materials.delete(materials.findByOperationIdAndId(opId, id)
                .orElseThrow(() -> notFound("Material consumption", id)));
    }

    // ── Quality variables ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<QualityVariableDto> listQualityVariables(UUID orgId, UUID routeId, UUID opId) {
        requireOperation(orgId, routeId, opId);
        return qualityVariables.findByOperationIdOrderByCreatedAt(opId).stream()
                .map(e -> new QualityVariableDto(e.getId(), e.getInspectionCharacteristicId())).toList();
    }

    public QualityVariableDto addQualityVariable(UUID orgId, UUID routeId, UUID opId, QualityVariableDto req) {
        UUID org = requireDraftOperation(orgId, routeId, opId);
        QualityVariableRequirement e = new QualityVariableRequirement();
        e.setOrgId(org);
        e.setOperationId(opId);
        e.setInspectionCharacteristicId(req.inspectionCharacteristicId());
        QualityVariableRequirement saved = qualityVariables.save(e);
        return new QualityVariableDto(saved.getId(), saved.getInspectionCharacteristicId());
    }

    public void deleteQualityVariable(UUID orgId, UUID routeId, UUID opId, UUID id) {
        requireDraftOperation(orgId, routeId, opId);
        qualityVariables.delete(qualityVariables.findByOperationIdAndId(opId, id)
                .orElseThrow(() -> notFound("Quality variable", id)));
    }

    // ── Tooling ────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ToolingRequirementDto> listTooling(UUID orgId, UUID routeId, UUID opId) {
        requireOperation(orgId, routeId, opId);
        return tooling.findByOperationIdOrderByCreatedAt(opId).stream()
                .map(e -> new ToolingRequirementDto(e.getId(), e.getGageOrToolRef(), e.getDescription()))
                .toList();
    }

    public ToolingRequirementDto addTooling(UUID orgId, UUID routeId, UUID opId, ToolingRequirementDto req) {
        UUID org = requireDraftOperation(orgId, routeId, opId);
        ToolingRequirement e = new ToolingRequirement();
        e.setOrgId(org);
        e.setOperationId(opId);
        e.setGageOrToolRef(req.gageOrToolRef());
        e.setDescription(req.description());
        ToolingRequirement saved = tooling.save(e);
        return new ToolingRequirementDto(saved.getId(), saved.getGageOrToolRef(), saved.getDescription());
    }

    public void deleteTooling(UUID orgId, UUID routeId, UUID opId, UUID id) {
        requireDraftOperation(orgId, routeId, opId);
        tooling.delete(tooling.findByOperationIdAndId(opId, id)
                .orElseThrow(() -> notFound("Tooling requirement", id)));
    }

    // ── Skills ───────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SkillRequirementDto> listSkills(UUID orgId, UUID routeId, UUID opId) {
        requireOperation(orgId, routeId, opId);
        return skills.findByOperationIdOrderByCreatedAt(opId).stream()
                .map(e -> new SkillRequirementDto(e.getId(), e.getSkillId())).toList();
    }

    public SkillRequirementDto addSkill(UUID orgId, UUID routeId, UUID opId, SkillRequirementDto req) {
        UUID org = requireDraftOperation(orgId, routeId, opId);
        SkillRequirement e = new SkillRequirement();
        e.setOrgId(org);
        e.setOperationId(opId);
        e.setSkillId(req.skillId());
        SkillRequirement saved = skills.save(e);
        return new SkillRequirementDto(saved.getId(), saved.getSkillId());
    }

    public void deleteSkill(UUID orgId, UUID routeId, UUID opId, UUID id) {
        requireDraftOperation(orgId, routeId, opId);
        skills.delete(skills.findByOperationIdAndId(opId, id)
                .orElseThrow(() -> notFound("Skill requirement", id)));
    }

    // ── Work-instruction link ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<WorkInstructionLinkDto> listWorkInstructions(UUID orgId, UUID routeId, UUID opId) {
        requireOperation(orgId, routeId, opId);
        return workInstructions.findByOperationIdOrderByCreatedAt(opId).stream()
                .map(e -> new WorkInstructionLinkDto(e.getId(), e.getWorkInstructionId())).toList();
    }

    public WorkInstructionLinkDto addWorkInstruction(UUID orgId, UUID routeId, UUID opId,
                                                     WorkInstructionLinkDto req) {
        UUID org = requireDraftOperation(orgId, routeId, opId);
        WorkInstructionLink e = new WorkInstructionLink();
        e.setOrgId(org);
        e.setOperationId(opId);
        e.setWorkInstructionId(req.workInstructionId());
        WorkInstructionLink saved = workInstructions.save(e);
        return new WorkInstructionLinkDto(saved.getId(), saved.getWorkInstructionId());
    }

    public void deleteWorkInstruction(UUID orgId, UUID routeId, UUID opId, UUID id) {
        requireDraftOperation(orgId, routeId, opId);
        workInstructions.delete(workInstructions.findByOperationIdAndId(opId, id)
                .orElseThrow(() -> notFound("Work instruction link", id)));
    }

    // ── STEP-file reference ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<StepFileReferenceDto> listStepFiles(UUID orgId, UUID routeId, UUID opId) {
        requireOperation(orgId, routeId, opId);
        return stepFiles.findByOperationIdOrderByCreatedAt(opId).stream()
                .map(e -> new StepFileReferenceDto(e.getId(), e.getReference())).toList();
    }

    public StepFileReferenceDto addStepFile(UUID orgId, UUID routeId, UUID opId, StepFileReferenceDto req) {
        UUID org = requireDraftOperation(orgId, routeId, opId);
        StepFileReference e = new StepFileReference();
        e.setOrgId(org);
        e.setOperationId(opId);
        e.setReference(req.reference());
        StepFileReference saved = stepFiles.save(e);
        return new StepFileReferenceDto(saved.getId(), saved.getReference());
    }

    public void deleteStepFile(UUID orgId, UUID routeId, UUID opId, UUID id) {
        requireDraftOperation(orgId, routeId, opId);
        stepFiles.delete(stepFiles.findByOperationIdAndId(opId, id)
                .orElseThrow(() -> notFound("STEP file reference", id)));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────

    /** Read path: validate the route is in-org and the operation exists (no DRAFT requirement). */
    private void requireOperation(UUID orgId, UUID routeId, UUID opId) {
        routes.findByOrgIdAndId(orgId, routeId)
                .orElseThrow(() -> new RoutingNotFoundException("Route not found: " + routeId));
        operations.findByRouteIdAndId(routeId, opId)
                .orElseThrow(() -> new RoutingNotFoundException("Operation not found: " + opId));
    }

    private static RoutingNotFoundException notFound(String label, UUID id) {
        return new RoutingNotFoundException(label + " not found: " + id);
    }
}
