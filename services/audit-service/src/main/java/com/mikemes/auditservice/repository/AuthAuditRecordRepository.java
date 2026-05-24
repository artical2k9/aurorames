package com.mikemes.auditservice.repository;

import com.mikemes.audit.domain.AuthAuditRecord;
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
public interface AuthAuditRecordRepository extends JpaRepository<AuthAuditRecord, UUID> {

    Optional<AuthAuditRecord> findByEventId(UUID eventId);

    @Query("""
            SELECT r FROM AuthAuditRecord r
            WHERE (:userId IS NULL OR r.userId = :userId)
              AND (:eventType IS NULL OR r.eventType = :eventType)
              AND r.occurredAt BETWEEN :from AND :to
            """)
    Page<AuthAuditRecord> findByFilters(
            @Param("userId") String userId,
            @Param("eventType") String eventType,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable);
}
