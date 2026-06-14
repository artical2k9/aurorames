package com.mes.labour.training.repository;

import com.mes.labour.training.domain.TrainingAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TrainingAttendanceRepository extends JpaRepository<TrainingAttendance, UUID> {

    @Query("""
            SELECT a FROM TrainingAttendance a
            JOIN FETCH a.trainingEvent e
            WHERE e.orgId = :orgId AND a.employee.id = :employeeId
            ORDER BY e.trainingDate DESC
            """)
    List<TrainingAttendance> findAllForEmployee(@Param("orgId") UUID orgId,
                                                @Param("employeeId") UUID employeeId);

    @Query("""
            SELECT a FROM TrainingAttendance a
            JOIN FETCH a.employee
            WHERE a.trainingEvent.id = :eventId
            """)
    List<TrainingAttendance> findAllForEvent(@Param("eventId") UUID eventId);
}
