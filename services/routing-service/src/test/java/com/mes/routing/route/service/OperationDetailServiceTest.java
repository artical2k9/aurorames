package com.mes.routing.route.service;

import com.mes.routing.referencedata.domain.LabourCode;
import com.mes.routing.referencedata.domain.LabourPlanType;
import com.mes.routing.referencedata.domain.WorkCentre;
import com.mes.routing.referencedata.repository.LabourCodeRepository;
import com.mes.routing.referencedata.repository.LabourPlanTypeRepository;
import com.mes.routing.referencedata.repository.WorkCentreRepository;
import com.mes.routing.route.api.dto.OperationDetailDtos.LabourPlanLineDto;
import com.mes.routing.route.api.dto.OperationDetailDtos.OperationResourceDto;
import com.mes.routing.route.api.dto.OperationDetailDtos.SkillRequirementDto;
import com.mes.routing.route.domain.Basis;
import com.mes.routing.route.domain.LabourActivityType;
import com.mes.routing.route.domain.LabourPlanLine;
import com.mes.routing.route.domain.OperationResource;
import com.mes.routing.route.domain.Route;
import com.mes.routing.route.domain.RouteOperation;
import com.mes.routing.route.domain.RouteStatus;
import com.mes.routing.route.domain.SkillRequirement;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationDetailServiceTest {

    static final UUID ORG = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID ROUTE = UUID.randomUUID();
    static final UUID OP = UUID.randomUUID();
    static final UUID ID = UUID.randomUUID();

    @Mock RouteRepository routes;
    @Mock RouteOperationRepository operations;
    @Mock WorkCentreRepository workCentres;
    @Mock LabourPlanTypeRepository labourPlanTypes;
    @Mock LabourCodeRepository labourCodes;
    @Mock OperationResourceRepository resources;
    @Mock LabourPlanLineRepository labourPlanLines;
    @Mock MaterialConsumptionRepository materials;
    @Mock QualityVariableRequirementRepository qualityVariables;
    @Mock ToolingRequirementRepository tooling;
    @Mock SkillRequirementRepository skills;
    @Mock WorkInstructionLinkRepository workInstructions;
    @Mock StepFileReferenceRepository stepFiles;

    @InjectMocks OperationDetailService service;

    private void draftOperationExists() {
        Route route = new Route();
        route.setOrgId(ORG);
        route.setStatus(RouteStatus.DRAFT);
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route));
        lenient().when(operations.findByRouteIdAndId(ROUTE, OP)).thenReturn(Optional.of(new RouteOperation()));
    }

    @Test
    void addResource_routeNotFound_throwsNotFound() {
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addResource(ORG, ROUTE, OP,
                new OperationResourceDto(null, UUID.randomUUID())))
                .isInstanceOf(RoutingNotFoundException.class);
    }

    @Test
    void addResource_routeNotDraft_throwsConflict() {
        Route route = new Route();
        route.setOrgId(ORG);
        route.setStatus(RouteStatus.APPROVED);
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.of(route));
        assertThatThrownBy(() -> service.addResource(ORG, ROUTE, OP,
                new OperationResourceDto(null, UUID.randomUUID())))
                .isInstanceOf(RoutingConflictException.class);
    }

    @Test
    void addResource_unknownWorkCentre_throwsNotFound() {
        draftOperationExists();
        UUID wc = UUID.randomUUID();
        when(workCentres.findByOrgIdAndId(ORG, wc)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addResource(ORG, ROUTE, OP, new OperationResourceDto(null, wc)))
                .isInstanceOf(RoutingNotFoundException.class);
    }

    @Test
    void addResource_valid_persists() {
        draftOperationExists();
        UUID wc = UUID.randomUUID();
        when(workCentres.findByOrgIdAndId(ORG, wc)).thenReturn(Optional.of(new WorkCentre()));
        when(resources.save(any(OperationResource.class))).thenAnswer(inv -> inv.getArgument(0));
        OperationResourceDto dto = service.addResource(ORG, ROUTE, OP, new OperationResourceDto(null, wc));
        assertThat(dto.workCentreId()).isEqualTo(wc);
    }

    @Test
    void addLabourPlan_unknownPlanType_throwsNotFound() {
        draftOperationExists();
        UUID planType = UUID.randomUUID();
        when(labourPlanTypes.findByOrgIdAndId(ORG, planType)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addLabourPlan(ORG, ROUTE, OP,
                new LabourPlanLineDto(null, LabourActivityType.SETUP, planType, null,
                        BigDecimal.ONE, Basis.PER_LOT)))
                .isInstanceOf(RoutingNotFoundException.class);
    }

    @Test
    void addLabourPlan_unknownCode_throwsNotFound() {
        draftOperationExists();
        UUID code = UUID.randomUUID();
        when(labourCodes.findByOrgIdAndId(ORG, code)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addLabourPlan(ORG, ROUTE, OP,
                new LabourPlanLineDto(null, LabourActivityType.RUN, null, code, BigDecimal.ONE, Basis.PER_ITEM)))
                .isInstanceOf(RoutingNotFoundException.class);
    }

    @Test
    void addLabourPlan_valid_persists() {
        draftOperationExists();
        UUID planType = UUID.randomUUID();
        UUID code = UUID.randomUUID();
        when(labourPlanTypes.findByOrgIdAndId(ORG, planType)).thenReturn(Optional.of(new LabourPlanType()));
        when(labourCodes.findByOrgIdAndId(ORG, code)).thenReturn(Optional.of(new LabourCode()));
        when(labourPlanLines.save(any(LabourPlanLine.class))).thenAnswer(inv -> inv.getArgument(0));
        LabourPlanLineDto dto = service.addLabourPlan(ORG, ROUTE, OP,
                new LabourPlanLineDto(null, LabourActivityType.SETUP, planType, code,
                        new BigDecimal("2.5"), Basis.PER_LOT));
        assertThat(dto.timeValue()).isEqualByComparingTo("2.5");
    }

    @Test
    void addSkillAndDelete() {
        draftOperationExists();
        when(skills.save(any(SkillRequirement.class))).thenAnswer(inv -> inv.getArgument(0));
        UUID skillId = UUID.randomUUID();
        SkillRequirementDto dto = service.addSkill(ORG, ROUTE, OP, new SkillRequirementDto(null, skillId));
        assertThat(dto.skillId()).isEqualTo(skillId);

        SkillRequirement existing = new SkillRequirement();
        when(skills.findByOperationIdAndId(OP, ID)).thenReturn(Optional.of(existing));
        service.deleteSkill(ORG, ROUTE, OP, ID);
        verify(skills).delete(existing);
    }

    @Test
    void delete_unknownDetail_throwsNotFound() {
        draftOperationExists();
        when(tooling.findByOperationIdAndId(OP, ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteTooling(ORG, ROUTE, OP, ID))
                .isInstanceOf(RoutingNotFoundException.class);
    }

    @Test
    void list_unknownRoute_throwsNotFound() {
        when(routes.findByOrgIdAndId(ORG, ROUTE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.listResources(ORG, ROUTE, OP))
                .isInstanceOf(RoutingNotFoundException.class);
    }
}
