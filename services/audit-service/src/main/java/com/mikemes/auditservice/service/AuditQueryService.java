package com.mikemes.auditservice.service;

import com.mikemes.audit.domain.AuditRecord;
import com.mikemes.audit.domain.AuthAuditRecord;
import com.mikemes.auditservice.api.dto.AuditRecordDto;
import com.mikemes.auditservice.api.dto.AuthAuditRecordDto;
import com.mikemes.auditservice.repository.AuditRecordRepository;
import com.mikemes.auditservice.repository.AuthAuditRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class AuditQueryService {

    private final AuditRecordRepository auditRepository;
    private final AuthAuditRecordRepository authRepository;

    public AuditQueryService(AuditRecordRepository auditRepository,
                             AuthAuditRecordRepository authRepository) {
        this.auditRepository = auditRepository;
        this.authRepository = authRepository;
    }

    public Page<AuditRecordDto> findEntityHistory(
            String entityType,
            String entityId,
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable) {
        return auditRepository.findByEntityTypeAndEntityIdAndOccurredAtBetween(
            entityType, entityId, from, to, pageable)
            .map(this::toAuditDto);
    }

    public Page<AuditRecordDto> findAuditRecords(
            String entityType,
            String userId,
            String action,
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable) {
        return auditRepository.findByFilters(entityType, userId, action, from, to, pageable)
            .map(this::toAuditDto);
    }

    public Page<AuthAuditRecordDto> findAuthEvents(
            String userId,
            String eventType,
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable) {
        return authRepository.findByFilters(userId, eventType, from, to, pageable)
            .map(this::toAuthDto);
    }

    private AuditRecordDto toAuditDto(AuditRecord record) {
        return new AuditRecordDto(
            record.getId(),
            record.getEventId(),
            record.getEventType(),
            record.getEntityType(),
            record.getEntityId(),
            record.getUserId(),
            record.getServiceSource(),
            record.getAction(),
            record.getOccurredAt(),
            record.getPreviousState(),
            record.getNewState(),
            record.getChecksum(),
            record.getSchemaVersion()
        );
    }

    private AuthAuditRecordDto toAuthDto(AuthAuditRecord record) {
        return new AuthAuditRecordDto(
            record.getId(),
            record.getEventId(),
            record.getEventType(),
            record.getUserId(),
            record.getClientId(),
            record.getIpAddress(),
            record.getSessionId(),
            record.getRealmId(),
            record.getOccurredAt(),
            record.getDetails()
        );
    }
}
