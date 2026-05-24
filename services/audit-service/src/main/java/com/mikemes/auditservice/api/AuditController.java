package com.mikemes.auditservice.api;

import com.mikemes.auditservice.api.dto.AuthAuditRecordDto;
import com.mikemes.auditservice.service.AuditQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/audit")
public class AuditController {

    private final AuditQueryService queryService;

    public AuditController(AuditQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/auth-events")
    @PreAuthorize("hasRole('AUDIT_READ') or hasRole('SYSTEM_ADMIN')")
    public Page<AuthAuditRecordDto> getAuthEvents(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        OffsetDateTime effectiveFrom = from != null ? from : OffsetDateTime.now().minusDays(30);
        OffsetDateTime effectiveTo = to != null ? to : OffsetDateTime.now();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        return queryService.findAuthEvents(userId, eventType, effectiveFrom, effectiveTo, pageable);
    }
}
