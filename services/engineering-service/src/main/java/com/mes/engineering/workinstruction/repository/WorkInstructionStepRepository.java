package com.mes.engineering.workinstruction.repository;

import com.mes.engineering.workinstruction.domain.WorkInstructionStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkInstructionStepRepository extends JpaRepository<WorkInstructionStep, UUID> {

    List<WorkInstructionStep> findByRevisionIdOrderByStepNumberAsc(UUID revisionId);

    Optional<WorkInstructionStep> findByRevisionIdAndStepNumber(UUID revisionId, Integer stepNumber);

    Optional<WorkInstructionStep> findByRevisionIdAndId(UUID revisionId, UUID id);

    long countByRevisionId(UUID revisionId);
}
