package com.mes.routing.route.api.dto;

import com.mes.routing.route.domain.Basis;
import com.mes.routing.route.domain.LabourActivityType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/** Request/response DTOs for operation-detail child resources (US3). */
public final class OperationDetailDtos {

    private OperationDetailDtos() {
    }

    public record OperationResourceDto(UUID id, @NotNull UUID workCentreId) {
    }

    public record LabourPlanLineDto(
            UUID id,
            @NotNull LabourActivityType labourActivityType,
            UUID labourPlanTypeId,
            UUID labourCodeId,
            @NotNull @Positive BigDecimal timeValue,
            @NotNull Basis basis) {
    }

    public record MaterialConsumptionDto(UUID id, @NotNull UUID bomLineId, boolean mandatory) {
    }

    public record QualityVariableDto(UUID id, @NotNull UUID inspectionCharacteristicId) {
    }

    public record ToolingRequirementDto(UUID id, @NotNull @Size(max = 100) String gageOrToolRef,
                                        @Size(max = 500) String description) {
    }

    public record SkillRequirementDto(UUID id, @NotNull UUID skillId) {
    }

    public record WorkInstructionLinkDto(UUID id, @NotNull UUID workInstructionId) {
    }

    public record StepFileReferenceDto(UUID id, @NotNull @Size(max = 500) String reference) {
    }
}
