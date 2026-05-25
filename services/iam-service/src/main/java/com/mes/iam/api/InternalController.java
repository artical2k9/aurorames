package com.mes.iam.api;

import com.mes.iam.kafka.KeycloakEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal")
public class InternalController {

    private static final Logger LOG = LoggerFactory.getLogger(InternalController.class);

    private final KeycloakEventPublisher eventPublisher;

    public InternalController(KeycloakEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @PostMapping("/keycloak-events")
    public ResponseEntity<Void> receiveKeycloakEvent(@RequestBody Map<String, Object> event) {
        String eventType = event.get("type") instanceof String s ? s : null;
        if (eventType != null && !eventType.isBlank()) {
            eventPublisher.publishEvent(eventType, event);
        } else {
            LOG.warn("Received Keycloak webhook with no event type — ignored");
        }
        return ResponseEntity.noContent().build();
    }
}
