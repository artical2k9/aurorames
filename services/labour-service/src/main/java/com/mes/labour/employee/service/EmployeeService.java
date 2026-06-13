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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class EmployeeService {

    private static final String EMPLOYEE_NOT_FOUND = "Employee not found: ";

    private final EmployeeRepository employeeRepository;

    public EmployeeService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
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
