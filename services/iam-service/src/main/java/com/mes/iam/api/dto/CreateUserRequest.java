package com.mes.iam.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.util.List;

public record CreateUserRequest(
        @NotBlank @Email
        String email,

        @NotBlank @Size(max = 100)
        String firstName,

        @NotBlank @Size(max = 100)
        String lastName,

        @NotEmpty
        List<String> roles,

        // nullable — when absent a KC email invite is sent instead
        @Nullable @Size(min = 8, max = 72)
        String initialPassword,

        // true = user must change on first login; ignored when initialPassword is absent
        boolean temporaryPassword,

        // Employee Number for the linked employee record that labour-service creates from the
        // iam.user.created event. Required — every user now also has an employee record.
        @NotBlank @Size(max = 50)
        String employeeNumber,

        // Optional hire date carried to the linked employee record.
        @Nullable
        LocalDate hireDate
) {}
