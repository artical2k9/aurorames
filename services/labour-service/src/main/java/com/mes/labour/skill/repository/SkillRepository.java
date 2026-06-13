package com.mes.labour.skill.repository;

import com.mes.labour.skill.domain.Skill;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SkillRepository extends JpaRepository<Skill, UUID> {

    Optional<Skill> findByOrgIdAndId(UUID orgId, UUID id);

    boolean existsByOrgIdAndSkillCode(UUID orgId, String skillCode);

    List<Skill> findAllByOrgIdAndIdIn(UUID orgId, Collection<UUID> ids);

    @Query("""
            SELECT s FROM Skill s
            WHERE s.orgId = :orgId
              AND (:active IS NULL OR s.active = :active)
              AND (:search IS NULL
                   OR LOWER(s.skillCode) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                   OR LOWER(s.name)      LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                   OR LOWER(s.category)  LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
            ORDER BY s.skillCode
            """)
    Page<Skill> search(@Param("orgId") UUID orgId,
                       @Param("search") String search,
                       @Param("active") Boolean active,
                       Pageable pageable);
}
