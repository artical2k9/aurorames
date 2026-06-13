package com.mes.quality.inspectionplan.repository;

import com.mes.quality.inspectionplan.domain.InspectionPlanRevision;
import com.mes.quality.inspectionplan.domain.RevisionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionPlanRevisionRepository extends JpaRepository<InspectionPlanRevision, UUID> {

    List<InspectionPlanRevision> findByInspectionPlanId(UUID inspectionPlanId);

    List<InspectionPlanRevision> findByInspectionPlanIdOrderByRevisionAsc(UUID inspectionPlanId);

    Optional<InspectionPlanRevision> findByInspectionPlanIdAndRevisionStatus(
            UUID inspectionPlanId, RevisionStatus revisionStatus);

    Optional<InspectionPlanRevision> findByInspectionPlanIdAndRevision(
            UUID inspectionPlanId, Integer revision);

    boolean existsByInspectionPlanIdAndRevisionStatus(
            UUID inspectionPlanId, RevisionStatus revisionStatus);
}
