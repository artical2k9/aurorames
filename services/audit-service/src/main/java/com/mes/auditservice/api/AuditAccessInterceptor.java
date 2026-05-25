package com.mes.auditservice.api;

import com.mes.audit.domain.AuditRecord;
import com.mes.auditservice.repository.AuditRecordRepository;
import com.mes.events.KafkaTopics;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public class AuditAccessInterceptor implements HandlerInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(AuditAccessInterceptor.class);
    private static final String AUDIT_ACCESS_TYPE = "AUDIT_ACCESS";
    private static final String ACCESS_ACTION = "READ";
    private static final String SERVICE_SOURCE = "audit-service";

    private final AuditRecordRepository repository;

    public AuditAccessInterceptor(AuditRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {
        try {
            String userId = resolveUserId();
            AuditRecord record = AuditRecord.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .eventType(KafkaTopics.MES_AUDIT_EVENTS)
                .entityType(AUDIT_ACCESS_TYPE)
                .entityId(request.getRequestURI())
                .userId(userId)
                .serviceSource(SERVICE_SOURCE)
                .action(ACCESS_ACTION)
                .occurredAt(OffsetDateTime.now(ZoneOffset.UTC))
                .checksum("access-log")
                .schemaVersion(1)
                .build();
            repository.save(record);
        } catch (Exception e) {
            LOG.warn("Failed to record audit access for URI={}", request.getRequestURI(), e);
        }
    }

    private String resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        return "anonymous";
    }
}
