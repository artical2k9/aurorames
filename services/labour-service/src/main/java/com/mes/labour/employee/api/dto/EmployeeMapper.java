package com.mes.labour.employee.api.dto;

import com.mes.labour.employee.domain.Employee;

public final class EmployeeMapper {

    private EmployeeMapper() {
    }

    public static EmployeeDto toDto(Employee employee) {
        EmployeeDto dto = new EmployeeDto();
        dto.setId(employee.getId());
        dto.setOrgId(employee.getOrgId());
        dto.setEmployeeNumber(employee.getEmployeeNumber());
        dto.setFirstName(employee.getFirstName());
        dto.setLastName(employee.getLastName());
        dto.setEmail(employee.getEmail());
        dto.setEmploymentStatus(employee.getEmploymentStatus().name());
        dto.setHireDate(employee.getHireDate());
        dto.setIamUserId(employee.getIamUserId());
        dto.setCustomFields(employee.getCustomFields());
        dto.setCreatedBy(employee.getCreatedBy());
        dto.setCreatedAt(employee.getCreatedAt());
        dto.setModifiedBy(employee.getModifiedBy());
        dto.setModifiedAt(employee.getModifiedAt());
        return dto;
    }
}
