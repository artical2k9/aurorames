package com.mikemes.auditservice.consumer;

import com.mikemes.events.audit.AuditEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class DuplicateEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(DuplicateEventHandler.class);

    public void handleDuplicate(AuditEventMessage message, Acknowledgment ack) {
        LOG.debug("Duplicate eventId={} entityType={} entityId={} — skipping",
            message.eventId(), message.entityType(), message.entityId());
        ack.acknowledge();
    }
}
