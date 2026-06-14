package com.mes.labour.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mes.labour.employee.service.EmployeeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit coverage for {@link UserCreatedEventListener} branches not exercised by the Kafka
 * integration test: missing orgId, malformed payloads, and date parsing (MES-117).
 */
@ExtendWith(MockitoExtension.class)
class UserCreatedEventListenerTest {

    @Mock EmployeeService employeeService;

    @Spy ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks UserCreatedEventListener listener;

    static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void onUserCreated_validMessage_createsEmployee() {
        String message = """
                {"userId":"kc-1","orgId":"00000000-0000-0000-0000-000000000001",
                 "email":"ada@test.com","firstName":"Ada","lastName":"Lovelace",
                 "employeeNumber":"EMP-001","hireDate":"2026-06-01"}""";

        listener.onUserCreated(message);

        verify(employeeService).createFromUser(ORG_ID, "kc-1", "EMP-001", "Ada", "Lovelace",
                "ada@test.com", LocalDate.of(2026, 6, 1));
    }

    @Test
    void onUserCreated_nullHireDate_passesNullDate() {
        String message = """
                {"userId":"kc-2","orgId":"00000000-0000-0000-0000-000000000001",
                 "email":"a@b.com","firstName":"A","lastName":"B","employeeNumber":"EMP-002"}""";

        listener.onUserCreated(message);

        verify(employeeService).createFromUser(ORG_ID, "kc-2", "EMP-002", "A", "B", "a@b.com", null);
    }

    @Test
    void onUserCreated_blankHireDate_passesNullDate() {
        String message = """
                {"userId":"kc-3","orgId":"00000000-0000-0000-0000-000000000001",
                 "email":"a@b.com","firstName":"A","lastName":"B",
                 "employeeNumber":"EMP-003","hireDate":"  "}""";

        listener.onUserCreated(message);

        verify(employeeService).createFromUser(ORG_ID, "kc-3", "EMP-003", "A", "B", "a@b.com", null);
    }

    @Test
    void onUserCreated_missingOrgId_skips() {
        String message = """
                {"userId":"kc-4","email":"a@b.com","firstName":"A","lastName":"B",
                 "employeeNumber":"EMP-004"}""";

        listener.onUserCreated(message);

        verify(employeeService, never()).createFromUser(any(), any(), any(), any(), any(),
                any(), any());
    }

    @Test
    void onUserCreated_malformedJson_isSwallowed() {
        listener.onUserCreated("{not valid json");

        verify(employeeService, never()).createFromUser(any(), any(), any(), any(), any(),
                any(), any());
    }

    @Test
    void onUserCreated_invalidOrgIdUuid_isSwallowed() {
        String message = """
                {"userId":"kc-5","orgId":"not-a-uuid","email":"a@b.com",
                 "firstName":"A","lastName":"B","employeeNumber":"EMP-005"}""";

        listener.onUserCreated(message);

        verify(employeeService, never()).createFromUser(any(), any(), any(), any(), any(),
                any(), any());
    }

    @Test
    void onUserCreated_invalidHireDate_isSwallowed() {
        String message = """
                {"userId":"kc-6","orgId":"00000000-0000-0000-0000-000000000001",
                 "email":"a@b.com","firstName":"A","lastName":"B",
                 "employeeNumber":"EMP-006","hireDate":"31-31-2026"}""";

        listener.onUserCreated(message);

        verify(employeeService, never()).createFromUser(eq(ORG_ID), any(), any(), any(), any(),
                any(), any());
    }
}
