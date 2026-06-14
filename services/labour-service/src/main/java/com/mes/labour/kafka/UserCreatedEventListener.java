package com.mes.labour.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mes.labour.employee.service.EmployeeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Consumes {@code iam.user.created} (published by iam-service) and creates the linked employee
 * record (employee↔IAM unification — MES-117). The payload is a JSON string; parsing failures and
 * bad data are logged and swallowed so a single poison message does not stall the partition.
 */
@Component
public class UserCreatedEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(UserCreatedEventListener.class);

    private final EmployeeService employeeService;
    private final ObjectMapper objectMapper;

    public UserCreatedEventListener(EmployeeService employeeService, ObjectMapper objectMapper) {
        this.employeeService = employeeService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${labour.events.user-created-topic:iam.user.created}",
            groupId = "${spring.kafka.consumer.group-id:labour-service}")
    public void onUserCreated(String message) {
        try {
            UserCreatedEvent event = objectMapper.readValue(message, UserCreatedEvent.class);
            if (event.orgId() == null) {
                LOG.warn("iam.user.created event missing orgId — skipping: {}", message);
                return;
            }
            employeeService.createFromUser(
                    UUID.fromString(event.orgId()),
                    event.userId(),
                    event.employeeNumber(),
                    event.firstName(),
                    event.lastName(),
                    event.email(),
                    parseDate(event.hireDate()));
        } catch (JsonProcessingException | IllegalArgumentException | DateTimeParseException ex) {
            // Poison message (bad JSON / UUID / date) — log and skip; transient infra errors
            // (e.g. DB) are left to propagate so the listener container retries.
            LOG.error("Skipping malformed iam.user.created event: {}", message, ex);
        }
    }

    private static LocalDate parseDate(String value) {
        return (value == null || value.isBlank()) ? null : LocalDate.parse(value);
    }
}
