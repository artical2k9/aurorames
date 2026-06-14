package com.mes.labour.employee.service;

import com.mes.labour.employee.api.dto.CreateEmployeeRequest;
import com.mes.labour.employee.api.dto.EmployeeDto;
import com.mes.labour.employee.api.dto.EmployeeMapper;
import com.mes.labour.employee.api.dto.PatchEmployeeRequest;
import com.mes.labour.employee.domain.Employee;
import com.mes.labour.employee.domain.EmploymentStatus;
import com.mes.labour.employee.repository.EmployeeRepository;
import com.mes.labour.service.LabourConflictException;
import com.mes.labour.service.LabourNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@Transactional
public class EmployeeService {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeService.class);

    private static final String EMPLOYEE_NOT_FOUND = "Employee not found: ";

    private final EmployeeRepository employeeRepository;

    public EmployeeService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    /**
     * Create the employee record linked to a newly-created IAM user (consumed from
     * {@code iam.user.created}). Idempotent: a redelivered event for an already-linked user is a
     * no-op, and a clashing employee number is logged and skipped rather than crashing the
     * consumer.
     */
    public void createFromUser(UUID orgId, String iamUserId, String employeeNumber,
                               String firstName, String lastName, String email, LocalDate hireDate) {
        if (iamUserId == null || iamUserId.isBlank()) {
            LOG.warn("iam.user.created event missing userId for org {} — skipping", orgId);
            return;
        }
        if (employeeRepository.existsByOrgIdAndIamUserId(orgId, iamUserId)) {
            return; // already linked — idempotent on redelivery
        }
        if (employeeNumber != null
                && employeeRepository.existsByOrgIdAndEmployeeNumber(orgId, employeeNumber)) {
            LOG.warn("Employee number {} already exists for org {}; not auto-creating from user {}",
                    employeeNumber, orgId, iamUserId);
            return;
        }
        Employee employee = new Employee();
        employee.setOrgId(orgId);
        employee.setEmployeeNumber(employeeNumber);
        employee.setFirstName(firstName);
        employee.setLastName(lastName);
        employee.setEmail(email);
        employee.setHireDate(hireDate);
        employee.setIamUserId(iamUserId);
        employee.setEmploymentStatus(EmploymentStatus.ACTIVE);
        employeeRepository.save(employee);
        LOG.info("Created employee {} linked to IAM user {} (org {})",
                employeeNumber, iamUserId, orgId);
    }

    public EmployeeDto create(UUID orgId, CreateEmployeeRequest req) {
        if (employeeRepository.existsByOrgIdAndEmployeeNumber(orgId, req.getEmployeeNumber())) {
            throw new LabourConflictException(
                    "Employee number already exists: " + req.getEmployeeNumber());
        }
        if (req.getIamUserId() != null && !req.getIamUserId().isBlank()
                && employeeRepository.existsByOrgIdAndIamUserId(orgId, req.getIamUserId())) {
            throw new LabourConflictException(
                    "IAM user is already linked to another employee: " + req.getIamUserId());
        }

        Employee employee = new Employee();
        employee.setOrgId(orgId);
        employee.setEmployeeNumber(req.getEmployeeNumber());
        employee.setFirstName(req.getFirstName());
        employee.setLastName(req.getLastName());
        employee.setEmail(req.getEmail());
        employee.setHireDate(req.getHireDate());
        if (req.getIamUserId() != null && !req.getIamUserId().isBlank()) {
            employee.setIamUserId(req.getIamUserId());
        }
        employee.setCustomFields(req.getCustomFields());
        return EmployeeMapper.toDto(employeeRepository.save(employee));
    }

    @Transactional(readOnly = true)
    public EmployeeDto get(UUID orgId, UUID employeeId) {
        return EmployeeMapper.toDto(requireEmployee(orgId, employeeId));
    }

    @Transactional(readOnly = true)
    public EmployeeDto getByIamUserId(UUID orgId, String iamUserId) {
        return employeeRepository.findByOrgIdAndIamUserId(orgId, iamUserId)
                .map(EmployeeMapper::toDto)
                .orElseThrow(() -> new LabourNotFoundException(
                        "No employee linked to IAM user: " + iamUserId));
    }

    @Transactional(readOnly = true)
    public Page<EmployeeDto> list(UUID orgId, String search, EmploymentStatus status,
                                  Pageable pageable) {
        String normalisedSearch = (search == null || search.isBlank()) ? null : search;
        return employeeRepository.search(orgId, normalisedSearch, status, pageable)
                .map(EmployeeMapper::toDto);
    }

    public EmployeeDto patch(UUID orgId, UUID employeeId, PatchEmployeeRequest req) {
        Employee employee = requireEmployee(orgId, employeeId);

        if (req.getIamUserId() != null) {
            if (req.getIamUserId().isBlank()) {
                employee.setIamUserId(null);
            } else if (!req.getIamUserId().equals(employee.getIamUserId())) {
                if (employeeRepository.existsByOrgIdAndIamUserId(orgId, req.getIamUserId())) {
                    throw new LabourConflictException(
                            "IAM user is already linked to another employee: " + req.getIamUserId());
                }
                employee.setIamUserId(req.getIamUserId());
            }
        }
        if (req.getFirstName() != null) {
            employee.setFirstName(req.getFirstName());
        }
        if (req.getLastName() != null) {
            employee.setLastName(req.getLastName());
        }
        if (req.getEmail() != null) {
            employee.setEmail(req.getEmail());
        }
        if (req.getEmploymentStatus() != null) {
            employee.setEmploymentStatus(req.getEmploymentStatus());
        }
        if (req.getHireDate() != null) {
            employee.setHireDate(req.getHireDate());
        }
        if (req.getCustomFields() != null) {
            employee.setCustomFields(req.getCustomFields());
        }
        return EmployeeMapper.toDto(employeeRepository.save(employee));
    }

    private Employee requireEmployee(UUID orgId, UUID employeeId) {
        return employeeRepository.findByOrgIdAndId(orgId, employeeId)
                .orElseThrow(() -> new LabourNotFoundException(EMPLOYEE_NOT_FOUND + employeeId));
    }
}
