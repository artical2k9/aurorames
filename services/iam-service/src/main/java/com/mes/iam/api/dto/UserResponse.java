package com.mes.iam.api.dto;

import java.util.List;

public record UserResponse(
        String id,
        String email,
        String firstName,
        String lastName,
        boolean enabled,
        List<String> roles,
        boolean passwordChangeRequired
) {}
