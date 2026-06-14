package com.mes.labour.employee.service;

import com.mes.labour.employee.domain.Employee;
import com.mes.labour.employee.domain.EmploymentStatus;
import com.mes.labour.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link EmployeeService#createFromUser} skip/error branches that the
 * Kafka integration test does not exercise (MES-117).
 */
@ExtendWith(MockitoExtension.class)
class EmployeeServiceCreateFromUserTest {

    @Mock EmployeeRepository employeeRepository;

    @InjectMocks EmployeeService employeeService;

    static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final String IAM_USER_ID = "kc-user-1";

    @Test
    void createFromUser_nullUserId_skipsWithoutSaving() {
        employeeService.createFromUser(ORG_ID, null, "EMP-001", "A", "B", "a@b.com", null);

        verify(employeeRepository, never()).save(any());
    }

    @Test
    void createFromUser_blankUserId_skipsWithoutSaving() {
        employeeService.createFromUser(ORG_ID, "  ", "EMP-001", "A", "B", "a@b.com", null);

        verify(employeeRepository, never()).save(any());
    }

    @Test
    void createFromUser_alreadyLinked_isIdempotent() {
        when(employeeRepository.existsByOrgIdAndIamUserId(ORG_ID, IAM_USER_ID)).thenReturn(true);

        employeeService.createFromUser(ORG_ID, IAM_USER_ID, "EMP-001", "A", "B", "a@b.com", null);

        verify(employeeRepository, never()).save(any());
    }

    @Test
    void createFromUser_employeeNumberAlreadyExists_skipsWithoutSaving() {
        when(employeeRepository.existsByOrgIdAndIamUserId(ORG_ID, IAM_USER_ID)).thenReturn(false);
        when(employeeRepository.existsByOrgIdAndEmployeeNumber(ORG_ID, "EMP-001")).thenReturn(true);

        employeeService.createFromUser(ORG_ID, IAM_USER_ID, "EMP-001", "A", "B", "a@b.com", null);

        verify(employeeRepository, never()).save(any());
    }

    @Test
    void createFromUser_newUser_savesActiveLinkedEmployee() {
        when(employeeRepository.existsByOrgIdAndIamUserId(ORG_ID, IAM_USER_ID)).thenReturn(false);
        when(employeeRepository.existsByOrgIdAndEmployeeNumber(ORG_ID, "EMP-001")).thenReturn(false);
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDate hireDate = LocalDate.of(2026, 6, 1);
        employeeService.createFromUser(ORG_ID, IAM_USER_ID, "EMP-001", "Ada", "Lovelace",
                "ada@test.com", hireDate);

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(captor.capture());
        Employee saved = captor.getValue();
        assertThat(saved.getOrgId()).isEqualTo(ORG_ID);
        assertThat(saved.getIamUserId()).isEqualTo(IAM_USER_ID);
        assertThat(saved.getEmployeeNumber()).isEqualTo("EMP-001");
        assertThat(saved.getFirstName()).isEqualTo("Ada");
        assertThat(saved.getLastName()).isEqualTo("Lovelace");
        assertThat(saved.getEmail()).isEqualTo("ada@test.com");
        assertThat(saved.getHireDate()).isEqualTo(hireDate);
        assertThat(saved.getEmploymentStatus()).isEqualTo(EmploymentStatus.ACTIVE);
    }

    @Test
    void createFromUser_nullEmployeeNumber_savesWithoutNumberCheck() {
        when(employeeRepository.existsByOrgIdAndIamUserId(ORG_ID, IAM_USER_ID)).thenReturn(false);
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        employeeService.createFromUser(ORG_ID, IAM_USER_ID, null, "Ada", "Lovelace",
                "ada@test.com", null);

        verify(employeeRepository, never()).existsByOrgIdAndEmployeeNumber(any(), any());
        verify(employeeRepository).save(any(Employee.class));
    }
}
