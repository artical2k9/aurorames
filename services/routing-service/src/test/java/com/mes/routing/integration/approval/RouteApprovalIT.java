package com.mes.routing.integration.approval;

import com.mes.routing.integration.BaseIntegrationTest;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.awaitility.Awaitility.await;

/** US7 — route approval via e-signature, significant-process gating, and the approved event. */
class RouteApprovalIT extends BaseIntegrationTest {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAP =
            new ParameterizedTypeReference<>() { };

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private String admin() {
        return buildToken(DEV_ORG, List.of("SYSTEM_ADMIN"));
    }

    private String adminWithRole(String role) {
        return buildToken(DEV_ORG, List.of("SYSTEM_ADMIN", role));
    }

    private String standardRouteTypeId() {
        return (String) restTemplate.exchange("/api/v1/routing/route-types", HttpMethod.GET,
                bearerRequest(admin()), LIST_OF_MAP).getBody().stream()
                .filter(m -> "STANDARD".equals(m.get("code"))).findFirst().orElseThrow().get("id");
    }

    private String createRoute() {
        return (String) restTemplate.exchange("/api/v1/routes", HttpMethod.POST,
                jsonRequest(admin(), Map.of("partId", UUID.randomUUID().toString(), "partRevision", "A",
                        "routeTypeId", standardRouteTypeId(), "reasonForRevision", "Initial")),
                Map.class).getBody().get("id");
    }

    private void addOperation(String routeId, Map<String, Object> body) {
        ResponseEntity<Map> r = restTemplate.exchange("/api/v1/routes/" + routeId + "/operations",
                HttpMethod.POST, jsonRequest(admin(), body), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private ResponseEntity<Map> post(String token, String path, Map<String, Object> body) {
        return restTemplate.exchange(path, HttpMethod.POST, jsonRequest(token, body), Map.class);
    }

    @Test
    void submitAndApprove_noSignificantProcess_becomesApprovedAndEmitsEvent() {
        String routeId = createRoute();
        addOperation(routeId, Map.of("operationNumber", 10, "sequenceNumber", 10));

        try (KafkaConsumer<String, String> consumer = approvedConsumer()) {
            assertThat(post(admin(), "/api/v1/routes/" + routeId + "/submit", Map.of()).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
            ResponseEntity<Map> approved = post(admin(), "/api/v1/routes/" + routeId + "/approve",
                    Map.of("password", "goodpass", "meaning", "Approved for production"));
            assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(approved.getBody().get("status")).isEqualTo("APPROVED");

            await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                    assertThat(pollFor(consumer, routeId)).isTrue());
        }
    }

    @Test
    void approve_badPassword_returns422() {
        String routeId = createRoute();
        addOperation(routeId, Map.of("operationNumber", 10, "sequenceNumber", 10));
        post(admin(), "/api/v1/routes/" + routeId + "/submit", Map.of());

        ResponseEntity<Map> r = post(admin(), "/api/v1/routes/" + routeId + "/approve",
                Map.of("password", "wrong", "meaning", "Approve"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void significantProcessRoute_requiresSmeApproverBeforeApproval() {
        String sigTypeId = (String) restTemplate.exchange("/api/v1/routing/significant-process-types",
                HttpMethod.POST, jsonRequest(admin(), Map.of("code", "WELD-" + UUID.randomUUID(),
                        "name", "Welding", "requiredApproverRole", "WELD_SME")), Map.class).getBody().get("id");
        String routeId = createRoute();
        addOperation(routeId, Map.of("operationNumber", 10, "sequenceNumber", 10,
                "significantProcessTypeId", sigTypeId));
        post(admin(), "/api/v1/routes/" + routeId + "/submit", Map.of());

        // Standard approval alone leaves the route pending with the SME type outstanding.
        ResponseEntity<Map> standard = post(admin(), "/api/v1/routes/" + routeId + "/approve",
                Map.of("password", "goodpass", "meaning", "Standard approval"));
        assertThat(standard.getBody().get("status")).isEqualTo("PENDING_APPROVAL");
        assertThat(standard.getBody().get("outstandingSignificantProcessTypeIds"))
                .asInstanceOf(list(String.class)).containsExactly(sigTypeId);

        // SME holder of WELD_SME completes the approval.
        ResponseEntity<Map> sme = post(adminWithRole("WELD_SME"), "/api/v1/routes/" + routeId + "/approve",
                Map.of("password", "goodpass", "meaning", "SME approval", "significantProcessTypeId", sigTypeId));
        assertThat(sme.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sme.getBody().get("status")).isEqualTo("APPROVED");
    }

    @Test
    void editingApprovedRoute_isRejectedUntilRevision() {
        String routeId = createRoute();
        addOperation(routeId, Map.of("operationNumber", 10, "sequenceNumber", 10));
        post(admin(), "/api/v1/routes/" + routeId + "/submit", Map.of());
        post(admin(), "/api/v1/routes/" + routeId + "/approve",
                Map.of("password", "goodpass", "meaning", "Approved"));

        ResponseEntity<Map> r = restTemplate.exchange("/api/v1/routes/" + routeId + "/operations",
                HttpMethod.POST, jsonRequest(admin(), Map.of("operationNumber", 20, "sequenceNumber", 20)),
                Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rejectPendingRoute_setsRejected() {
        String routeId = createRoute();
        addOperation(routeId, Map.of("operationNumber", 10, "sequenceNumber", 10));
        post(admin(), "/api/v1/routes/" + routeId + "/submit", Map.of());

        ResponseEntity<Map> r = post(admin(), "/api/v1/routes/" + routeId + "/reject",
                Map.of("password", "goodpass", "reason", "Wrong sequence"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().get("status")).isEqualTo("REJECTED");
    }

    private KafkaConsumer<String, String> approvedConsumer() {
        Properties p = new Properties();
        p.put("bootstrap.servers", bootstrapServers);
        p.put("group.id", "test-approved-" + UUID.randomUUID());
        p.put("key.deserializer", StringDeserializer.class.getName());
        p.put("value.deserializer", StringDeserializer.class.getName());
        p.put("auto.offset.reset", "earliest");
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(p);
        consumer.subscribe(List.of("routing.route.approved"));
        return consumer;
    }

    private boolean pollFor(KafkaConsumer<String, String> consumer, String routeId) {
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
        for (ConsumerRecord<String, String> record : records) {
            if (record.value() != null && record.value().contains(routeId)) {
                return true;
            }
        }
        return false;
    }
}
