package com.mes.labour.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Payload of the {@code iam.user.created} event (JSON string from iam-service). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserCreatedEvent(
        String userId,
        String orgId,
        String email,
        String firstName,
        String lastName,
        String employeeNumber,
        String hireDate) {
}
