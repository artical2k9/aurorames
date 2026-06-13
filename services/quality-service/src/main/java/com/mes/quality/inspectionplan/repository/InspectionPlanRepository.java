package com.mes.quality.inspectionplan.repository;

import com.mes.quality.inspectionplan.domain.InspectionPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InspectionPlanRepository extends JpaRepository<InspectionPlan, UUID> {

    Optional<InspectionPlan> findByOrgIdAndId(UUID orgId, UUID id);

    Optional<InspectionPlan> findByOrgIdAndItemId(UUID orgId, UUID itemId);

    boolean existsByOrgIdAndItemId(UUID orgId, UUID itemId);

    Page<InspectionPlan> findByOrgId(UUID orgId, Pageable pageable);

    @Query("""
            SELECT DISTINCT p FROM InspectionPlan p
            WHERE p.orgId = :orgId
              AND (LOWER(p.partNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR EXISTS (SELECT 1 FROM InspectionPlanRevision r
                              WHERE r.inspectionPlan = p
                                AND LOWER(r.name) LIKE LOWER(CONCAT('%', :search, '%'))))
            """)
    Page<InspectionPlan> search(@Param("orgId") UUID orgId,
                                @Param("search") String search,
                                Pageable pageable);
}
