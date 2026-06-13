package com.mes.engineering.workinstruction.repository;

import com.mes.engineering.workinstruction.domain.WorkInstruction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkInstructionRepository extends JpaRepository<WorkInstruction, UUID> {

    Optional<WorkInstruction> findByOrgIdAndId(UUID orgId, UUID id);

    Optional<WorkInstruction> findByOrgIdAndIdentifier(UUID orgId, String identifier);

    boolean existsByOrgIdAndIdentifier(UUID orgId, String identifier);

    Page<WorkInstruction> findByOrgIdAndDeletedFalse(UUID orgId, Pageable pageable);

    List<WorkInstruction> findByOrgIdAndIdentifierStartingWith(UUID orgId, String prefix);

    /**
     * Search by identifier or by any revision title. Split from the no-search path so the
     * untyped-null binding issue (Hibernate 6) never arises — this query always binds a
     * non-null trimmed term.
     */
    @Query("""
            SELECT DISTINCT wi FROM WorkInstruction wi
            WHERE wi.orgId = :orgId AND wi.deleted = false
              AND (LOWER(wi.identifier) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR EXISTS (SELECT 1 FROM WorkInstructionRevision r
                              WHERE r.workInstruction = wi
                                AND LOWER(r.title) LIKE LOWER(CONCAT('%', :term, '%'))))
            """)
    Page<WorkInstruction> search(@Param("orgId") UUID orgId, @Param("term") String term, Pageable pageable);
}
