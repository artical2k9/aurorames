package com.mes.routing.route.service;

import com.mes.routing.route.api.dto.FlowDtos.CreateStepRequest;
import com.mes.routing.route.api.dto.FlowDtos.GroupDto;
import com.mes.routing.route.api.dto.FlowDtos.GroupInput;
import com.mes.routing.route.api.dto.FlowDtos.MutuallyExclusiveSetDto;
import com.mes.routing.route.api.dto.FlowDtos.MutuallyExclusiveSetInput;
import com.mes.routing.route.api.dto.FlowDtos.PatchStepRequest;
import com.mes.routing.route.api.dto.FlowDtos.PutGroupsRequest;
import com.mes.routing.route.api.dto.FlowDtos.PutMutuallyExclusiveSetsRequest;
import com.mes.routing.route.api.dto.FlowDtos.StepDto;
import com.mes.routing.route.domain.MutuallyExclusiveLevel;
import com.mes.routing.route.domain.MutuallyExclusiveSet;
import com.mes.routing.route.domain.OperationGroup;
import com.mes.routing.route.domain.OperationStep;
import com.mes.routing.route.domain.Route;
import com.mes.routing.route.domain.RouteOperation;
import com.mes.routing.route.domain.RouteStatus;
import com.mes.routing.route.repository.MutuallyExclusiveSetRepository;
import com.mes.routing.route.repository.OperationGroupRepository;
import com.mes.routing.route.repository.OperationStepRepository;
import com.mes.routing.route.repository.RouteOperationRepository;
import com.mes.routing.route.repository.RouteRepository;
import com.mes.routing.service.RoutingConflictException;
import com.mes.routing.service.RoutingNotFoundException;
import com.mes.routing.service.RoutingValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Advanced flow control (US4/US5/US6): operation groups, operation steps, and mutually-exclusive
 * sets at operation/group/step level. All edits require the owning route to be DRAFT. Normal vs
 * Parallel is always derived from the relevant sequence number; Optional/Mutually-Exclusive are
 * explicit. A mutually-exclusive set is valid only when its members all share one parallel
 * sequence number (≥2 members), so it is necessarily a subset of a parallel set.
 */
@Service
@Transactional
public class OperationFlowService {

    private final RouteRepository routes;
    private final RouteOperationRepository operations;
    private final OperationGroupRepository groups;
    private final OperationStepRepository steps;
    private final MutuallyExclusiveSetRepository meSets;

    public OperationFlowService(RouteRepository routes, RouteOperationRepository operations,
                                OperationGroupRepository groups, OperationStepRepository steps,
                                MutuallyExclusiveSetRepository meSets) {
        this.routes = routes;
        this.operations = operations;
        this.groups = groups;
        this.steps = steps;
        this.meSets = meSets;
    }

    // ── Groups (US5) ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<GroupDto> listGroups(UUID orgId, UUID routeId) {
        requireRoute(orgId, routeId);
        return groups.findByRouteIdOrderByGroupSequenceNumberAsc(routeId).stream()
                .map(g -> toGroupDto(routeId, g)).toList();
    }

    public List<GroupDto> putGroups(UUID orgId, UUID routeId, PutGroupsRequest req) {
        Route route = requireDraftRoute(orgId, routeId);
        List<RouteOperation> routeOps =
                operations.findByRouteIdOrderBySequenceNumberAscOperationNumberAsc(routeId);
        Set<UUID> validOpIds = routeOps.stream().map(RouteOperation::getId)
                .collect(java.util.stream.Collectors.toSet());

        Set<UUID> assigned = new HashSet<>();
        for (GroupInput in : req.groups()) {
            for (UUID opId : safe(in.operationIds())) {
                if (!validOpIds.contains(opId)) {
                    throw new RoutingValidationException("Operation does not belong to this route: " + opId);
                }
                if (!assigned.add(opId)) {
                    throw new RoutingValidationException("Operation assigned to more than one group: " + opId);
                }
            }
        }

        // Full replace: detach all operations, drop existing groups, then rebuild.
        routeOps.forEach(op -> op.setGroupId(null));
        operations.saveAll(routeOps);
        groups.deleteByRouteId(routeId);

        for (GroupInput in : req.groups()) {
            OperationGroup g = new OperationGroup();
            g.setOrgId(route.getOrgId());
            g.setRouteId(routeId);
            g.setName(in.name());
            g.setGroupSequenceNumber(in.groupSequenceNumber());
            g.setOptional(in.optional());
            OperationGroup saved = groups.save(g);
            for (UUID opId : safe(in.operationIds())) {
                routeOps.stream().filter(op -> op.getId().equals(opId)).findFirst()
                        .ifPresent(op -> op.setGroupId(saved.getId()));
            }
        }
        operations.saveAll(routeOps);
        return listGroups(orgId, routeId);
    }

    // ── Steps (US6) ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<StepDto> listSteps(UUID orgId, UUID routeId, UUID opId) {
        requireOperation(orgId, routeId, opId);
        return steps.findByOperationIdOrderByStepSequenceNumberAscStepNumberAsc(opId).stream()
                .map(s -> toStepDto(opId, s)).toList();
    }

    public StepDto addStep(UUID orgId, UUID routeId, UUID opId, CreateStepRequest req) {
        UUID org = requireDraftOperation(orgId, routeId, opId);
        if (steps.existsByOperationIdAndStepNumber(opId, req.stepNumber())) {
            throw new RoutingConflictException("Step number already exists on operation: " + req.stepNumber());
        }
        OperationStep s = new OperationStep();
        s.setOrgId(org);
        s.setOperationId(opId);
        s.setStepNumber(req.stepNumber());
        s.setStepSequenceNumber(req.stepSequenceNumber());
        s.setDescription(req.description());
        s.setOptional(req.optional());
        return toStepDto(opId, steps.save(s));
    }

    public StepDto patchStep(UUID orgId, UUID routeId, UUID opId, UUID stepId, PatchStepRequest req) {
        requireDraftOperation(orgId, routeId, opId);
        OperationStep s = steps.findByOperationIdAndId(opId, stepId)
                .orElseThrow(() -> new RoutingNotFoundException("Step not found: " + stepId));
        if (req.stepNumber() != null && req.stepNumber() != s.getStepNumber()
                && steps.existsByOperationIdAndStepNumber(opId, req.stepNumber())) {
            throw new RoutingConflictException("Step number already exists on operation: " + req.stepNumber());
        }
        if (req.stepNumber() != null) {
            s.setStepNumber(req.stepNumber());
        }
        if (req.stepSequenceNumber() != null) {
            s.setStepSequenceNumber(req.stepSequenceNumber());
        }
        if (req.description() != null) {
            s.setDescription(req.description());
        }
        if (req.optional() != null) {
            s.setOptional(req.optional());
        }
        return toStepDto(opId, steps.save(s));
    }

    public void deleteStep(UUID orgId, UUID routeId, UUID opId, UUID stepId) {
        requireDraftOperation(orgId, routeId, opId);
        steps.delete(steps.findByOperationIdAndId(opId, stepId)
                .orElseThrow(() -> new RoutingNotFoundException("Step not found: " + stepId)));
    }

    // ── Mutually-exclusive sets (US4/US5/US6) ──────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MutuallyExclusiveSetDto> listMutuallyExclusiveSets(UUID orgId, UUID routeId) {
        requireRoute(orgId, routeId);
        return meSets.findByRouteIdOrderByLevelAscSequenceNumberAsc(routeId).stream()
                .map(this::toMeDto).toList();
    }

    public List<MutuallyExclusiveSetDto> putMutuallyExclusiveSets(UUID orgId, UUID routeId,
                                                                  PutMutuallyExclusiveSetsRequest req) {
        Route route = requireDraftRoute(orgId, routeId);
        meSets.deleteByRouteIdAndLevel(routeId, MutuallyExclusiveLevel.OPERATION);
        meSets.deleteByRouteIdAndLevel(routeId, MutuallyExclusiveLevel.GROUP);
        meSets.deleteByRouteIdAndLevel(routeId, MutuallyExclusiveLevel.STEP);
        for (MutuallyExclusiveSetInput in : req.sets()) {
            int sequence = resolveSharedSequence(routeId, in);
            MutuallyExclusiveSet e = new MutuallyExclusiveSet();
            e.setOrgId(route.getOrgId());
            e.setRouteId(routeId);
            e.setLevel(in.level());
            e.setSequenceNumber(sequence);
            e.setMemberIds(in.memberIds());
            meSets.save(e);
        }
        return listMutuallyExclusiveSets(orgId, routeId);
    }

    /** Validates membership and returns the single parallel sequence number the members share. */
    private int resolveSharedSequence(UUID routeId, MutuallyExclusiveSetInput in) {
        List<UUID> members = in.memberIds();
        if (new HashSet<>(members).size() != members.size()) {
            throw new RoutingValidationException("Mutually-exclusive members must be distinct");
        }
        List<Integer> sequences = switch (in.level()) {
            case OPERATION -> members.stream().map(id -> operations.findByRouteIdAndId(routeId, id)
                    .orElseThrow(() -> meInvalid("operation", id)).getSequenceNumber()).toList();
            case GROUP -> members.stream().map(id -> groups.findByRouteIdAndId(routeId, id)
                    .orElseThrow(() -> meInvalid("group", id)).getGroupSequenceNumber()).toList();
            case STEP -> stepSequences(routeId, members);
        };
        int first = sequences.get(0);
        if (!sequences.stream().allMatch(s -> s == first)) {
            throw new RoutingValidationException(
                    "Mutually exclusivity is only available within a parallel sequence: "
                            + "members must all share one sequence number");
        }
        return first;
    }

    private List<Integer> stepSequences(UUID routeId, List<UUID> members) {
        Set<UUID> operationIds = new HashSet<>();
        List<Integer> sequences = members.stream().map(id -> {
            OperationStep s = steps.findById(id).orElseThrow(() -> meInvalid("step", id));
            if (operations.findByRouteIdAndId(routeId, s.getOperationId()).isEmpty()) {
                throw meInvalid("step", id);
            }
            operationIds.add(s.getOperationId());
            return s.getStepSequenceNumber();
        }).toList();
        if (operationIds.size() != 1) {
            throw new RoutingValidationException(
                    "Mutually-exclusive steps must all belong to the same operation");
        }
        return sequences;
    }

    private static RoutingValidationException meInvalid(String level, UUID id) {
        return new RoutingValidationException("Not a valid " + level + " on this route: " + id);
    }

    // ── Mapping ────────────────────────────────────────────────────────────────────

    private GroupDto toGroupDto(UUID routeId, OperationGroup g) {
        String derived = groups.countByRouteIdAndGroupSequenceNumber(routeId, g.getGroupSequenceNumber()) > 1
                ? "PARALLEL" : "NORMAL";
        List<UUID> opIds = operations.findByRouteIdOrderBySequenceNumberAscOperationNumberAsc(routeId).stream()
                .filter(op -> g.getId().equals(op.getGroupId())).map(RouteOperation::getId).toList();
        return new GroupDto(g.getId(), g.getName(), g.getGroupSequenceNumber(), g.isOptional(), derived, opIds);
    }

    private StepDto toStepDto(UUID opId, OperationStep s) {
        String derived = steps.countByOperationIdAndStepSequenceNumber(opId, s.getStepSequenceNumber()) > 1
                ? "PARALLEL" : "NORMAL";
        return new StepDto(s.getId(), s.getStepNumber(), s.getStepSequenceNumber(),
                s.getDescription(), s.isOptional(), derived);
    }

    private MutuallyExclusiveSetDto toMeDto(MutuallyExclusiveSet e) {
        return new MutuallyExclusiveSetDto(e.getId(), e.getLevel(), e.getSequenceNumber(), e.getMemberIds());
    }

    // ── Guards ─────────────────────────────────────────────────────────────────────

    private static <T> List<T> safe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private Route requireRoute(UUID orgId, UUID routeId) {
        return routes.findByOrgIdAndId(orgId, routeId)
                .orElseThrow(() -> new RoutingNotFoundException("Route not found: " + routeId));
    }

    private Route requireDraftRoute(UUID orgId, UUID routeId) {
        Route route = requireRoute(orgId, routeId);
        if (route.getStatus() != RouteStatus.DRAFT) {
            throw new RoutingConflictException("Editable only while the route is DRAFT (status "
                    + route.getStatus() + "); start a revision");
        }
        return route;
    }

    private void requireOperation(UUID orgId, UUID routeId, UUID opId) {
        requireRoute(orgId, routeId);
        operations.findByRouteIdAndId(routeId, opId)
                .orElseThrow(() -> new RoutingNotFoundException("Operation not found: " + opId));
    }

    private UUID requireDraftOperation(UUID orgId, UUID routeId, UUID opId) {
        Route route = requireDraftRoute(orgId, routeId);
        operations.findByRouteIdAndId(routeId, opId)
                .orElseThrow(() -> new RoutingNotFoundException("Operation not found: " + opId));
        return route.getOrgId();
    }
}
