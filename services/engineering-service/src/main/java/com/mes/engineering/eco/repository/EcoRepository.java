package com.mes.engineering.eco.repository;

import com.mes.engineering.eco.domain.EngineeringChangeOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EcoRepository extends JpaRepository<EngineeringChangeOrder, UUID> {

    Optional<EngineeringChangeOrder> findByOrgIdAndId(UUID orgId, UUID id);

    Page<EngineeringChangeOrder> findAllByOrgId(UUID orgId, Pageable pageable);

    @Query(nativeQuery = true, value = """
            SELECT COUNT(eco.id)
            FROM engineering.engineering_change_order eco
            JOIN engineering.eco_affected_item eai ON eai.eco_id = eco.id
            WHERE eco.org_id = CAST(:orgId AS uuid)
              AND eai.affected_item_id = CAST(:itemId AS uuid)
              AND eco.status IN ('DRAFT', 'APPROVED')
            """)
    long countOpenEcosForItemId(@Param("orgId") UUID orgId, @Param("itemId") UUID itemId);
}
