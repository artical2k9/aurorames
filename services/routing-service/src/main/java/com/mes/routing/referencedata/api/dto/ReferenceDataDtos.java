package com.mes.routing.referencedata.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Request/response DTOs for the routing reference-data Settings submodule. */
public final class ReferenceDataDtos {

    private ReferenceDataDtos() {
    }

    public record WorkCentreDto(
            UUID id,
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 5000) String description,
            boolean active) {
    }

    public record LabourPlanTypeDto(
            UUID id,
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 200) String name,
            boolean seeded,
            boolean active) {
    }

    public record LabourCodeDto(
            UUID id,
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 200) String name,
            UUID labourPlanTypeId,
            boolean active) {
    }

    public record RouteTypeDto(
            UUID id,
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 200) String name,
            boolean isStandard,
            boolean seeded,
            boolean active) {
    }

    public record SignificantProcessTypeDto(
            UUID id,
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 100) String requiredApproverRole,
            boolean active) {
    }

    public record SupplierDto(
            UUID id,
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 200) String name,
            boolean active) {
    }
}
