package com.mikemes.auditservice.repository;

import com.mikemes.audit.domain.AuditRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditRecordRepository extends JpaRepository<AuditRecord, UUID> {

    Optional<AuditRecord> findByEventId(UUID eventId);

    Page<AuditRecord> findByEntityTypeAndEntityIdAndOccurredAtBetween(
            String entityType,
            String entityId,
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable);

    @Query("""
            SELECT r FROM AuditRecord r
            WHERE (:entityType IS NULL OR r.entityType = :entityType)
              AND (:userId IS NULL OR r.userId = :userId)
              AND (:action IS NULL OR r.action = :action)
              AND r.occurredAt BETWEEN :from AND :to
            """)
    Page<AuditRecord> findByFilters(
            @Param("entityType") String entityType,
            @Param("userId") String userId,
            @Param("action") String action,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable);

    @Query("""
            SELECT r FROM AuditRecord r
            WHERE r.occurredAt BETWEEN :from AND :to
            ORDER BY r.occurredAt ASC
            """)
    java.util.List<AuditRecord> findAllByOccurredAtBetweenOrderByOccurredAt(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    Page<AuditRecord> findByOccurredAtBetween(OffsetDateTime from, OffsetDateTime to, Pageable pageable);
}
