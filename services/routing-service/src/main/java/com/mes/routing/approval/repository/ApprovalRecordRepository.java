package com.mes.routing.approval.repository;

import com.mes.routing.approval.domain.ApprovalRecord;
import com.mes.routing.approval.domain.ApprovalSubjectType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApprovalRecordRepository extends JpaRepository<ApprovalRecord, UUID> {

    List<ApprovalRecord> findByRouteIdOrderByCreatedAt(UUID routeId);

    List<ApprovalRecord> findBySubjectTypeAndSubjectIdAndRevision(
            ApprovalSubjectType subjectType, UUID subjectId, int revision);
}
