package com.mes.engineering.workinstruction.repository;

import com.mes.engineering.workinstruction.domain.RevisionStatus;
import com.mes.engineering.workinstruction.domain.WorkInstructionRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkInstructionRevisionRepository
        extends JpaRepository<WorkInstructionRevision, UUID> {

    List<WorkInstructionRevision> findByWorkInstructionId(UUID workInstructionId);

    List<WorkInstructionRevision> findByWorkInstructionIdOrderByRevisionAsc(UUID workInstructionId);

    Optional<WorkInstructionRevision> findByWorkInstructionIdAndRevisionStatus(
            UUID workInstructionId, RevisionStatus revisionStatus);

    boolean existsByWorkInstructionIdAndRevisionStatus(
            UUID workInstructionId, RevisionStatus revisionStatus);
}
