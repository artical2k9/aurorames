package com.mes.routing.route.service;

import com.mes.routing.referencedata.repository.LabourPlanTypeRepository;
import com.mes.routing.referencedata.repository.SignificantProcessTypeRepository;
import com.mes.routing.referencedata.repository.SupplierRepository;
import com.mes.routing.route.api.dto.RouteDtos.CreateOperationRequest;
import com.mes.routing.route.api.dto.RouteDtos.OperationDto;
import com.mes.routing.route.api.dto.RouteDtos.PatchOperationRequest;
import com.mes.routing.route.domain.LabourPlanLine;
import com.mes.routing.route.domain.LabourType;
import com.mes.routing.route.domain.Route;
import com.mes.routing.route.domain.RouteOperation;
import com.mes.routing.route.domain.RouteStatus;
import com.mes.routing.route.repository.LabourPlanLineRepository;
import com.mes.routing.route.repository.RouteOperationRepository;
import com.mes.routing.route.repository.RouteRepository;
import com.mes.routing.service.RoutingConflictException;
import com.mes.routing.service.RoutingNotFoundException;
import com.mes.routing.service.RoutingValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Route operation authoring (US2). Operations are editable only while the route is DRAFT.
 * Type is derived from the sequence number: Normal when unique within the route, Parallel
 * when shared. Optional/OSP are explicit toggles (advanced flow control lands in US4).
 */
@Service
@Transactional
public class RouteOperationService {

    private final RouteRepository routes;
    private final RouteOperationRepository operations;
    private final SignificantProcessTypeRepository significantProcessTypes;
    private final SupplierRepository suppliers;
    private final LabourPlanLineRepository labourPlanLines;
    private final LabourPlanTypeRepository labourPlanTypes;
    private final RouteLockGuard lockGuard;

    public RouteOperationService(RouteRepository routes,
                                 RouteOperationRepository operations,
                                 SignificantProcessTypeRepository significantProcessTypes,
                                 SupplierRepository suppliers,
                                 LabourPlanLineRepository labourPlanLines,
                                 LabourPlanTypeRepository labourPlanTypes,
                                 RouteLockGuard lockGuard) {
        this.routes = routes;
        this.operations = operations;
        this.significantProcessTypes = significantProcessTypes;
        this.suppliers = suppliers;
        this.labourPlanLines = labourPlanLines;
        this.labourPlanTypes = labourPlanTypes;
        this.lockGuard = lockGuard;
    }

    @Transactional(readOnly = true)
    public List<OperationDto> list(UUID orgId, UUID routeId) {
        requireRoute(orgId, routeId);
        return operations.findByRouteIdOrderBySequenceNumberAscOperationNumberAsc(routeId)
                .stream().map(op -> toDto(op, routeId)).toList();
    }

    @Transactional(readOnly = true)
    public OperationDto get(UUID orgId, UUID routeId, UUID opId) {
        requireRoute(orgId, routeId);
        return toDto(operations.findByRouteIdAndId(routeId, opId)
                .orElseThrow(() -> new RoutingNotFoundException("Operation not found: " + opId)), routeId);
    }

    public OperationDto add(UUID orgId, UUID routeId, CreateOperationRequest req) {
        Route route = requireDraftRoute(orgId, routeId);
        if (operations.existsByRouteIdAndOperationNumber(routeId, req.operationNumber())) {
            throw new RoutingConflictException(
                    "Operation number already exists on route: " + req.operationNumber());
        }
        validateReferences(orgId, req.significantProcessTypeId(), req.supplierId());

        RouteOperation op = new RouteOperation();
        op.setOrgId(route.getOrgId());
        op.setRouteId(routeId);
        op.setOperationNumber(req.operationNumber());
        op.setSequenceNumber(req.sequenceNumber());
        op.setDescription(req.description());
        op.setOptional(req.optional());
        op.setOsp(req.osp());
        op.setSignificantProcessTypeId(req.significantProcessTypeId());
        op.setSupplierId(req.supplierId());
        op.setLabourType(parseLabourType(req.labourType()));
        op.setClocking(req.clocking() == null || req.clocking());
        RouteOperation saved = operations.save(op);
        requireOspLabourResource(saved);
        return toDto(saved, routeId);
    }

    public OperationDto patch(UUID orgId, UUID routeId, UUID opId, PatchOperationRequest req) {
        requireDraftRoute(orgId, routeId);
        RouteOperation op = operations.findByRouteIdAndId(routeId, opId)
                .orElseThrow(() -> new RoutingNotFoundException("Operation not found: " + opId));
        if (req.operationNumber() != null && req.operationNumber() != op.getOperationNumber()
                && operations.existsByRouteIdAndOperationNumber(routeId, req.operationNumber())) {
            throw new RoutingConflictException(
                    "Operation number already exists on route: " + req.operationNumber());
        }
        validateReferences(orgId, req.significantProcessTypeId(), req.supplierId());
        if (req.operationNumber() != null) {
            op.setOperationNumber(req.operationNumber());
        }
        if (req.sequenceNumber() != null) {
            op.setSequenceNumber(req.sequenceNumber());
        }
        if (req.description() != null) {
            op.setDescription(req.description());
        }
        if (req.optional() != null) {
            op.setOptional(req.optional());
        }
        if (req.osp() != null) {
            op.setOsp(req.osp());
        }
        if (req.significantProcessTypeId() != null) {
            op.setSignificantProcessTypeId(req.significantProcessTypeId());
        }
        if (req.supplierId() != null) {
            op.setSupplierId(req.supplierId());
        }
        if (req.labourType() != null) {
            op.setLabourType(parseLabourType(req.labourType()));
        }
        if (req.clocking() != null) {
            op.setClocking(req.clocking());
        }
        RouteOperation saved = operations.save(op);
        requireOspLabourResource(saved);
        return toDto(saved, routeId);
    }

    /**
     * An OSP operation must carry an OSP-type labour resource (FR-009a edge case): the OSP labour
     * plan line is what triggers the ERP requisition. Add the OSP labour line before flagging the
     * operation OSP. Filters in Java to avoid the Hibernate 6.5 parameterised-join pitfall.
     */
    private void requireOspLabourResource(RouteOperation op) {
        if (!op.isOsp()) {
            return;
        }
        Set<UUID> planTypeIds = labourPlanLines.findByOperationIdOrderByCreatedAt(op.getId()).stream()
                .map(LabourPlanLine::getLabourPlanTypeId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        boolean hasOspResource = !planTypeIds.isEmpty() && labourPlanTypes.findAllById(planTypeIds).stream()
                .anyMatch(t -> "OSP".equalsIgnoreCase(t.getCode()));
        if (!hasOspResource) {
            throw new RoutingValidationException("OSP operation requires an OSP-type labour resource; "
                    + "add an OSP labour plan line before flagging the operation OSP");
        }
    }

    public void delete(UUID orgId, UUID routeId, UUID opId) {
        requireDraftRoute(orgId, routeId);
        RouteOperation op = operations.findByRouteIdAndId(routeId, opId)
                .orElseThrow(() -> new RoutingNotFoundException("Operation not found: " + opId));
        operations.delete(op);
    }

    private void validateReferences(UUID orgId, UUID significantProcessTypeId, UUID supplierId) {
        if (significantProcessTypeId != null
                && significantProcessTypes.findByOrgIdAndId(orgId, significantProcessTypeId).isEmpty()) {
            throw new RoutingNotFoundException(
                    "Significant process type not found: " + significantProcessTypeId);
        }
        if (supplierId != null && suppliers.findByOrgIdAndId(orgId, supplierId).isEmpty()) {
            throw new RoutingNotFoundException("Supplier not found: " + supplierId);
        }
    }

    private LabourType parseLabourType(String value) {
        if (value == null || value.isBlank()) {
            return LabourType.DIRECT;
        }
        try {
            return LabourType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new RoutingValidationException("Labour type must be DIRECT or INDIRECT");
        }
    }

    private Route requireRoute(UUID orgId, UUID routeId) {
        return routes.findByOrgIdAndId(orgId, routeId)
                .orElseThrow(() -> new RoutingNotFoundException("Route not found: " + routeId));
    }

    private Route requireDraftRoute(UUID orgId, UUID routeId) {
        Route route = requireRoute(orgId, routeId);
        if (route.getStatus() != RouteStatus.DRAFT) {
            throw new RoutingConflictException(
                    "Operations are editable only while the route is DRAFT (status "
                            + route.getStatus() + "); start a revision");
        }
        lockGuard.requireHeld(route);
        return route;
    }

    private OperationDto toDto(RouteOperation op, UUID routeId) {
        String derived = operations.countByRouteIdAndSequenceNumber(routeId, op.getSequenceNumber()) > 1
                ? "PARALLEL" : "NORMAL";
        return new OperationDto(op.getId(), op.getOperationNumber(), op.getSequenceNumber(),
                op.getDescription(), derived, op.isOptional(), op.isOsp(),
                op.getSignificantProcessTypeId(), op.getSupplierId(), op.getGroupId(),
                op.isClocking(), (op.getLabourType() == null ? LabourType.DIRECT : op.getLabourType()).name(),
                op.getOperationRevision(),
                op.getOperationStatus().name());
    }
}
