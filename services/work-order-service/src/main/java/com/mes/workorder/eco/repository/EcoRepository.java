package com.mes.workorder.eco.repository;

import com.mes.workorder.eco.domain.EngineeringChangeOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface EcoRepository extends JpaRepository<EngineeringChangeOrder, UUID> {

    Optional<EngineeringChangeOrder> findByOrgIdAndId(UUID orgId, UUID id);

    Page<EngineeringChangeOrder> findAllByOrgId(UUID orgId, Pageable pageable);

    @Query(nativeQuery = true, value = """
            SELECT COUNT(eco.id)
            FROM work_order.engineering_change_order eco
            JOIN work_order.eco_affected_item eai ON eai.eco_id = eco.id
            WHERE eco.org_id = CAST(:orgId AS uuid)
              AND eai.item_id = CAST(:itemId AS uuid)
              AND eco.status IN ('DRAFT', 'APPROVED')
            """)
    long countOpenEcosForItemId(@Param("orgId") UUID orgId, @Param("itemId") UUID itemId);
}
