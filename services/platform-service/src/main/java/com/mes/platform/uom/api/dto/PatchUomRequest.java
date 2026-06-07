package com.mes.platform.uom.api.dto;

import jakarta.validation.constraints.Size;

public record PatchUomRequest(
        @Size(max = 20)  String  isoCode,
        @Size(max = 200) String  name,
        Boolean active
) {}
