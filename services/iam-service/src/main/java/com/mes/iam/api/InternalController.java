package com.mes.iam.api;

import com.mes.common.security.privilege.PrivilegeManifest;
import com.mes.iam.kafka.KeycloakEventPublisher;
import com.mes.iam.service.PrivilegeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/internal")
public class InternalController {

    private static final Logger LOG = LoggerFactory.getLogger(InternalController.class);

    private final KeycloakEventPublisher eventPublisher;
    private final PrivilegeService privilegeService;

    public InternalController(KeycloakEventPublisher eventPublisher, PrivilegeService privilegeService) {
        this.eventPublisher = eventPublisher;
        this.privilegeService = privilegeService;
    }

    @GetMapping("/privileges")
    public PrivilegeManifest getPrivilegeManifest() {
        Map<String, Set<String>> rolePrivileges = privilegeService.getPrivilegeMap().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new HashSet<>(e.getValue())));
        return new PrivilegeManifest(rolePrivileges);
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
