package com.mikemes.auditservice.consumer;

import com.mikemes.audit.checksum.ChecksumService;
import com.mikemes.audit.domain.AuditRecord;
import com.mikemes.audit.domain.AuthAuditRecord;
import com.mikemes.auditservice.repository.AuditRecordRepository;
import com.mikemes.auditservice.repository.AuthAuditRecordRepository;
import com.mikemes.events.KafkaTopics;
import com.mikemes.events.audit.AuditEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuditKafkaConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(AuditKafkaConsumer.class);

    private final AuditRecordRepository repository;
    private final AuthAuditRecordRepository authRepository;
    private final ChecksumService checksumService;
    private final DuplicateEventHandler duplicateEventHandler;

    public AuditKafkaConsumer(AuditRecordRepository repository,
                              AuthAuditRecordRepository authRepository,
                              ChecksumService checksumService,
                              DuplicateEventHandler duplicateEventHandler) {
        this.repository = repository;
        this.authRepository = authRepository;
        this.checksumService = checksumService;
        this.duplicateEventHandler = duplicateEventHandler;
    }

    @KafkaListener(
        topics = KafkaTopics.MES_AUDIT_EVENTS,
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(AuditEventMessage message, Acknowledgment ack) {
        if ("AUTH_EVENT".equals(message.eventType())) {
            consumeAuthEvent(message, ack);
            return;
        }
        try {
            AuditRecord record = mapToRecord(message);
            String checksum = checksumService.compute(record);
            AuditRecord withChecksum = AuditRecord.builder()
                .id(record.getId())
                .eventId(record.getEventId())
                .eventType(record.getEventType())
                .entityType(record.getEntityType())
                .entityId(record.getEntityId())
                .userId(record.getUserId())
                .serviceSource(record.getServiceSource())
                .action(record.getAction())
                .occurredAt(record.getOccurredAt())
                .previousState(record.getPreviousState())
                .newState(record.getNewState())
                .checksum(checksum)
                .schemaVersion(record.getSchemaVersion())
                .build();
            repository.save(withChecksum);
            ack.acknowledge();
            LOG.debug("Persisted audit record eventId={}", message.eventId());
        } catch (DataIntegrityViolationException e) {
            LOG.debug("Duplicate detection for eventId={}", message.eventId());
            duplicateEventHandler.handleDuplicate(message, ack);
        }
    }

    private void consumeAuthEvent(AuditEventMessage message, Acknowledgment ack) {
        try {
            AuthAuditRecord record = mapToAuthRecord(message);
            authRepository.save(record);
            ack.acknowledge();
            LOG.debug("Persisted auth audit record eventId={}", message.eventId());
        } catch (DataIntegrityViolationException e) {
            LOG.debug("Duplicate auth event detection for eventId={}", message.eventId());
            duplicateEventHandler.handleDuplicate(message, ack);
        }
    }

    private AuthAuditRecord mapToAuthRecord(AuditEventMessage message) {
        return AuthAuditRecord.builder()
            .id(UUID.randomUUID())
            .eventId(message.eventId())
            .eventType(message.eventType())
            .userId(message.userId())
            .realmId(message.serviceSource())
            .occurredAt(message.timestamp())
            .details(message.payload())
            .build();
    }

    private AuditRecord mapToRecord(AuditEventMessage message) {
        return AuditRecord.builder()
            .id(UUID.randomUUID())
            .eventId(message.eventId())
            .eventType(message.eventType())
            .entityType(message.entityType())
            .entityId(message.entityId())
            .userId(message.userId())
            .serviceSource(message.serviceSource())
            .action(message.action())
            .occurredAt(message.timestamp())
            .newState(message.payload())
            .schemaVersion(message.schemaVersion())
            .build();
    }
}
