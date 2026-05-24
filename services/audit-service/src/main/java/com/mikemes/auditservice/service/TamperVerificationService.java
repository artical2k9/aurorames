package com.mikemes.auditservice.service;

import com.mikemes.audit.checksum.ChecksumService;
import com.mikemes.audit.domain.AuditRecord;
import com.mikemes.auditservice.api.dto.VerificationResult;
import com.mikemes.auditservice.api.dto.VerificationViolation;
import com.mikemes.auditservice.repository.AuditRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class TamperVerificationService {

    private static final Logger LOG = LoggerFactory.getLogger(TamperVerificationService.class);
    private static final int BATCH_SIZE = 500;

    private final AuditRecordRepository repository;
    private final ChecksumService checksumService;

    public TamperVerificationService(AuditRecordRepository repository, ChecksumService checksumService) {
        this.repository = repository;
        this.checksumService = checksumService;
    }

    public VerificationResult verify(OffsetDateTime from, OffsetDateTime to) {
        List<VerificationViolation> violations = new ArrayList<>();
        long totalChecked = 0;
        int page = 0;
        Page<AuditRecord> batch;

        do {
            PageRequest pageable = PageRequest.of(page++, BATCH_SIZE, Sort.by("occurredAt"));
            batch = repository.findByOccurredAtBetween(from, to, pageable);
            for (AuditRecord record : batch.getContent()) {
                totalChecked++;
                String expected = checksumService.compute(record);
                if (!expected.equals(record.getChecksum())) {
                    LOG.warn("TAMPER_DETECTED recordId={} expected={} actual={}",
                        record.getId(), expected, record.getChecksum());
                    violations.add(new VerificationViolation(
                        record.getId(), "CHECKSUM_MISMATCH", expected, record.getChecksum()
                    ));
                }
            }
        } while (batch.hasNext());

        String status = violations.isEmpty() ? "PASS" : "FAIL";
        return new VerificationResult(status, totalChecked, violations, OffsetDateTime.now(ZoneOffset.UTC));
    }
}
