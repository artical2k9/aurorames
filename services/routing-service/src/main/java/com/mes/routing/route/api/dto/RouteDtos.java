package com.mes.routing.route.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/** Request/response DTOs for routes (header) and route operations. */
public final class RouteDtos {

    private RouteDtos() {
    }

    public record CreateRouteRequest(
            @NotNull UUID partId,
            @NotBlank @Size(max = 20) String partRevision,
            @NotNull UUID routeTypeId,
            UUID bomId,
            UUID bomRevisionId,
            UUID inspectionPlanRevisionId,
            @NotBlank @Size(max = 1000) String reasonForRevision,
            Map<String, Object> customFields) {
    }

    public record PatchRouteRequest(
            @Size(max = 1000) String reasonForRevision,
            UUID bomId,
            UUID bomRevisionId,
            UUID inspectionPlanRevisionId,
            Map<String, Object> customFields) {
    }

    public record RouteDto(
            UUID id,
            UUID partId,
            String partRevision,
            UUID routeTypeId,
            UUID bomId,
            UUID bomRevisionId,
            UUID inspectionPlanRevisionId,
            int revision,
            String status,
            String reasonForRevision,
            boolean bomRevisionSuperseded,
            boolean inspectionPlanRevisionSuperseded,
            Map<String, Object> customFields) {
    }

    public record CreateOperationRequest(
            @NotNull Integer operationNumber,
            @NotNull Integer sequenceNumber,
            @Size(max = 500) String description,
            boolean optional,
            boolean osp,
            UUID significantProcessTypeId,
            UUID supplierId,
            Boolean clocking) {
    }

    public record PatchOperationRequest(
            Integer operationNumber,
            Integer sequenceNumber,
            @Size(max = 500) String description,
            Boolean optional,
            Boolean osp,
            UUID significantProcessTypeId,
            UUID supplierId,
            Boolean clocking) {
    }

    public record OperationDto(
            UUID id,
            int operationNumber,
            int sequenceNumber,
            String description,
            String derivedType,
            boolean optional,
            boolean osp,
            UUID significantProcessTypeId,
            UUID supplierId,
            UUID groupId,
            boolean clocking,
            int operationRevision,
            String operationStatus) {
    }
}
