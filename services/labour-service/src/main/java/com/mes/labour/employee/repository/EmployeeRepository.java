package com.mes.labour.employee.repository;

import com.mes.labour.employee.domain.Employee;
import com.mes.labour.employee.domain.EmploymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    Optional<Employee> findByOrgIdAndId(UUID orgId, UUID id);

    boolean existsByOrgIdAndEmployeeNumber(UUID orgId, String employeeNumber);

    boolean existsByOrgIdAndIamUserId(UUID orgId, String iamUserId);

    Optional<Employee> findByOrgIdAndIamUserId(UUID orgId, String iamUserId);

    @Query("""
            SELECT e FROM Employee e
            WHERE e.orgId = :orgId
              AND (:status IS NULL OR e.employmentStatus = :status)
              AND (:search IS NULL
                   OR LOWER(e.employeeNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                   OR LOWER(e.firstName)      LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                   OR LOWER(e.lastName)       LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                   OR LOWER(e.email)          LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
            ORDER BY e.employeeNumber
            """)
    Page<Employee> search(@Param("orgId") UUID orgId,
                          @Param("search") String search,
                          @Param("status") EmploymentStatus status,
                          Pageable pageable);
}
