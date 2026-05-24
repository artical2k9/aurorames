package com.mikemes.auditservice.service;

import com.mikemes.audit.domain.AuthAuditRecord;
import com.mikemes.auditservice.api.dto.AuthAuditRecordDto;
import com.mikemes.auditservice.repository.AuthAuditRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class AuditQueryService {

    private final AuthAuditRecordRepository authRepository;

    public AuditQueryService(AuthAuditRecordRepository authRepository) {
        this.authRepository = authRepository;
    }

    public Page<AuthAuditRecordDto> findAuthEvents(
            String userId,
            String eventType,
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable) {
        return authRepository.findByFilters(userId, eventType, from, to, pageable)
            .map(this::toDto);
    }

    private AuthAuditRecordDto toDto(AuthAuditRecord record) {
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
