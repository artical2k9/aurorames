package com.mes.routing.route.api;

import com.mes.routing.route.api.dto.OperationDetailDtos.LabourPlanLineDto;
import com.mes.routing.route.api.dto.OperationDetailDtos.MaterialConsumptionDto;
import com.mes.routing.route.api.dto.OperationDetailDtos.OperationResourceDto;
import com.mes.routing.route.api.dto.OperationDetailDtos.QualityVariableDto;
import com.mes.routing.route.api.dto.OperationDetailDtos.SkillRequirementDto;
import com.mes.routing.route.api.dto.OperationDetailDtos.StepFileReferenceDto;
import com.mes.routing.route.api.dto.OperationDetailDtos.ToolingRequirementDto;
import com.mes.routing.route.api.dto.OperationDetailDtos.WorkInstructionLinkDto;
import com.mes.routing.route.domain.Basis;
import com.mes.routing.route.domain.LabourActivityType;
import com.mes.routing.route.service.OperationDetailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Delegation coverage for all 24 operation-detail endpoints. */
@ExtendWith(MockitoExtension.class)
class OperationDetailControllerTest {

    static final UUID ORG = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID R = UUID.randomUUID();
    static final UUID OP = UUID.randomUUID();
    static final UUID ID = UUID.randomUUID();

    @Mock OperationDetailService service;
    @InjectMocks OperationDetailController controller;

    private final Jwt jwt = Jwt.withTokenValue("t").header("alg", "none")
            .claim("org_id", ORG.toString()).build();

    @Test
    void resourceEndpoints() {
        OperationResourceDto d = new OperationResourceDto(ID, UUID.randomUUID());
        when(service.listResources(ORG, R, OP)).thenReturn(List.of(d));
        when(service.addResource(eq(ORG), eq(R), eq(OP), any())).thenReturn(d);
        assertThat(controller.listResources(jwt, R, OP)).containsExactly(d);
        assertThat(controller.addResource(jwt, R, OP, d).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.deleteResource(jwt, R, OP, ID).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).deleteResource(ORG, R, OP, ID);
    }

    @Test
    void labourPlanEndpoints() {
        LabourPlanLineDto d = new LabourPlanLineDto(ID, LabourActivityType.SETUP, null, null,
                BigDecimal.ONE, Basis.PER_LOT);
        when(service.listLabourPlan(ORG, R, OP)).thenReturn(List.of(d));
        when(service.addLabourPlan(eq(ORG), eq(R), eq(OP), any())).thenReturn(d);
        assertThat(controller.listLabourPlan(jwt, R, OP)).containsExactly(d);
        assertThat(controller.addLabourPlan(jwt, R, OP, d).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.deleteLabourPlan(jwt, R, OP, ID).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).deleteLabourPlan(ORG, R, OP, ID);
    }

    @Test
    void materialEndpoints() {
        MaterialConsumptionDto d = new MaterialConsumptionDto(ID, UUID.randomUUID(), true);
        when(service.listMaterials(ORG, R, OP)).thenReturn(List.of(d));
        when(service.addMaterial(eq(ORG), eq(R), eq(OP), any())).thenReturn(d);
        assertThat(controller.listMaterials(jwt, R, OP)).containsExactly(d);
        assertThat(controller.addMaterial(jwt, R, OP, d).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.deleteMaterial(jwt, R, OP, ID).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).deleteMaterial(ORG, R, OP, ID);
    }

    @Test
    void qualityVariableEndpoints() {
        QualityVariableDto d = new QualityVariableDto(ID, UUID.randomUUID());
        when(service.listQualityVariables(ORG, R, OP)).thenReturn(List.of(d));
        when(service.addQualityVariable(eq(ORG), eq(R), eq(OP), any())).thenReturn(d);
        assertThat(controller.listQualityVariables(jwt, R, OP)).containsExactly(d);
        assertThat(controller.addQualityVariable(jwt, R, OP, d).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.deleteQualityVariable(jwt, R, OP, ID).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).deleteQualityVariable(ORG, R, OP, ID);
    }

    @Test
    void toolingEndpoints() {
        ToolingRequirementDto d = new ToolingRequirementDto(ID, "G", "desc");
        when(service.listTooling(ORG, R, OP)).thenReturn(List.of(d));
        when(service.addTooling(eq(ORG), eq(R), eq(OP), any())).thenReturn(d);
        assertThat(controller.listTooling(jwt, R, OP)).containsExactly(d);
        assertThat(controller.addTooling(jwt, R, OP, d).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.deleteTooling(jwt, R, OP, ID).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).deleteTooling(ORG, R, OP, ID);
    }

    @Test
    void skillEndpoints() {
        SkillRequirementDto d = new SkillRequirementDto(ID, UUID.randomUUID());
        when(service.listSkills(ORG, R, OP)).thenReturn(List.of(d));
        when(service.addSkill(eq(ORG), eq(R), eq(OP), any())).thenReturn(d);
        assertThat(controller.listSkills(jwt, R, OP)).containsExactly(d);
        assertThat(controller.addSkill(jwt, R, OP, d).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.deleteSkill(jwt, R, OP, ID).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).deleteSkill(ORG, R, OP, ID);
    }

    @Test
    void workInstructionEndpoints() {
        WorkInstructionLinkDto d = new WorkInstructionLinkDto(ID, UUID.randomUUID());
        when(service.listWorkInstructions(ORG, R, OP)).thenReturn(List.of(d));
        when(service.addWorkInstruction(eq(ORG), eq(R), eq(OP), any())).thenReturn(d);
        assertThat(controller.listWorkInstructions(jwt, R, OP)).containsExactly(d);
        assertThat(controller.addWorkInstruction(jwt, R, OP, d).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.deleteWorkInstruction(jwt, R, OP, ID).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).deleteWorkInstruction(ORG, R, OP, ID);
    }

    @Test
    void stepFileEndpoints() {
        StepFileReferenceDto d = new StepFileReferenceDto(ID, "P.nc");
        when(service.listStepFiles(ORG, R, OP)).thenReturn(List.of(d));
        when(service.addStepFile(eq(ORG), eq(R), eq(OP), any())).thenReturn(d);
        assertThat(controller.listStepFiles(jwt, R, OP)).containsExactly(d);
        assertThat(controller.addStepFile(jwt, R, OP, d).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.deleteStepFile(jwt, R, OP, ID).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).deleteStepFile(ORG, R, OP, ID);
    }
}
