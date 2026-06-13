package com.mes.labour.certification.repository;

import com.mes.labour.certification.domain.Certification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CertificationRepository extends JpaRepository<Certification, UUID> {

    @Query("""
            SELECT c FROM Certification c
            JOIN FETCH c.skill
            JOIN FETCH c.employee
            WHERE c.orgId = :orgId AND c.id = :id
            """)
    Optional<Certification> findByOrgIdAndId(@Param("orgId") UUID orgId, @Param("id") UUID id);

    boolean existsByEmployeeIdAndSkillIdAndAwardDate(UUID employeeId, UUID skillId,
                                                     java.time.LocalDate awardDate);

    @Query("""
            SELECT c FROM Certification c
            JOIN FETCH c.skill
            WHERE c.orgId = :orgId AND c.employee.id = :employeeId
            ORDER BY c.awardDate DESC
            """)
    List<Certification> findAllForEmployee(@Param("orgId") UUID orgId,
                                           @Param("employeeId") UUID employeeId);

    @Query("""
            SELECT c FROM Certification c
            JOIN FETCH c.skill
            WHERE c.orgId = :orgId
              AND c.employee.id = :employeeId
              AND c.skill.id IN :skillIds
            """)
    List<Certification> findAllForEmployeeAndSkills(@Param("orgId") UUID orgId,
                                                    @Param("employeeId") UUID employeeId,
                                                    @Param("skillIds") Collection<UUID> skillIds);

    @Query("""
            SELECT c FROM Certification c
            JOIN FETCH c.skill
            JOIN FETCH c.employee
            WHERE c.orgId = :orgId
              AND (:employeeId IS NULL OR c.employee.id = :employeeId)
              AND (:skillId IS NULL OR c.skill.id = :skillId)
            ORDER BY c.awardDate DESC
            """)
    List<Certification> findAllFiltered(@Param("orgId") UUID orgId,
                                        @Param("employeeId") UUID employeeId,
                                        @Param("skillId") UUID skillId);
}
