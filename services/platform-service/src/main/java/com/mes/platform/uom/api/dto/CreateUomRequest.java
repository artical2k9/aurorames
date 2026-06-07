package com.mes.platform.uom.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUomRequest(
        @NotBlank @Size(max = 20)  String code,
        @Size(max = 20)            String isoCode,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 100) String category
) {}
