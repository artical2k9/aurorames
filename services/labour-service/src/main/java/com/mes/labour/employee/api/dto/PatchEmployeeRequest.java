package com.mes.labour.employee.api.dto;

import com.mes.labour.employee.domain.EmploymentStatus;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Map;

public class PatchEmployeeRequest {

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    @Size(max = 255)
    private String email;

    private EmploymentStatus employmentStatus;

    private LocalDate hireDate;

    // Explicit empty string clears the IAM link; null leaves it unchanged.
    @Size(max = 255)
    private String iamUserId;

    private Map<String, Object> customFields;

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public EmploymentStatus getEmploymentStatus() {
        return employmentStatus;
    }

    public void setEmploymentStatus(EmploymentStatus employmentStatus) {
        this.employmentStatus = employmentStatus;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public void setHireDate(LocalDate hireDate) {
        this.hireDate = hireDate;
    }

    public String getIamUserId() {
        return iamUserId;
    }

    public void setIamUserId(String iamUserId) {
        this.iamUserId = iamUserId;
    }

    public Map<String, Object> getCustomFields() {
        return customFields;
    }

    public void setCustomFields(Map<String, Object> customFields) {
        this.customFields = customFields;
    }
}
