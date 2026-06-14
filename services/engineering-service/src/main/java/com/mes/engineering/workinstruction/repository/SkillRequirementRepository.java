package com.mes.engineering.workinstruction.repository;

import com.mes.engineering.workinstruction.domain.SkillRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SkillRequirementRepository extends JpaRepository<SkillRequirement, UUID> {

    List<SkillRequirement> findByRevisionIdOrderBySkillCodeAsc(UUID revisionId);

    Optional<SkillRequirement> findByRevisionIdAndId(UUID revisionId, UUID id);

    boolean existsByRevisionIdAndSkillId(UUID revisionId, UUID skillId);
}
