package com.mes.engineering.integration.eco;

import com.mes.engineering.integration.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BomReleasedConsumerIT extends BaseIntegrationTest {

    static final String ORG_ID = "00000000-0000-0000-0000-000000000002";
    static final String ECO_BASE = "/api/v1/ecos";

    @Autowired
    KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    // CS1: bom.released with known ecoId → ECO outputBomIds contains bomId
    @Test
    @SuppressWarnings("unchecked")
    void bomReleasedEventAddsOutputBomIdToEco() throws Exception {
        String token = engineerToken();
        String ecoId = createEco(token, "Output BOM ECO");
        String bomId = UUID.randomUUID().toString();

        Map<String, Object> event = Map.of(
                "eventType", "bom.released",
                "bomId", bomId,
                "ecoId", ecoId,
                "orgId", ORG_ID,
                "parentItemId", UUID.randomUUID().toString(),
                "bomRevision", "REV-A"
        );
        kafkaTemplate.send("bom.released", bomId, event);

        // Poll until outputBomIds contains bomId or 10s deadline
        long deadline = System.currentTimeMillis() + 10_000L;
        List<Object> outputBomIds = null;
        while (System.currentTimeMillis() < deadline) {
            ResponseEntity<Map> ecoResp = restTemplate.exchange(
                    ECO_BASE + "/" + ecoId, HttpMethod.GET,
                    bearerRequest(token), Map.class);
            assertThat(ecoResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            outputBomIds = (List<Object>) ecoResp.getBody().get("outputBomIds");
            if (outputBomIds != null && outputBomIds.contains(bomId)) {
                break;
            }
            Thread.sleep(200);
        }

        assertThat(outputBomIds).as("outputBomIds should contain released BOM").contains(bomId);
    }

    // CS2: duplicate bom.released message → outputBomIds has exactly one entry (idempotent)
    @Test
    @SuppressWarnings("unchecked")
    void bomReleasedEventIsIdempotent() throws Exception {
        String token = engineerToken();
        String ecoId = createEco(token, "Idempotency ECO");
        String bomId = UUID.randomUUID().toString();

        Map<String, Object> event = Map.of(
                "eventType", "bom.released",
                "bomId", bomId,
                "ecoId", ecoId,
                "orgId", ORG_ID,
                "parentItemId", UUID.randomUUID().toString(),
                "bomRevision", "REV-B"
        );

        kafkaTemplate.send("bom.released", bomId, event);
        kafkaTemplate.send("bom.released", bomId, event);

        long deadline = System.currentTimeMillis() + 10_000L;
        List<Object> outputBomIds = null;
        while (System.currentTimeMillis() < deadline) {
            ResponseEntity<Map> ecoResp = restTemplate.exchange(
                    ECO_BASE + "/" + ecoId, HttpMethod.GET,
                    bearerRequest(token), Map.class);
            outputBomIds = (List<Object>) ecoResp.getBody().get("outputBomIds");
            if (outputBomIds != null && !outputBomIds.isEmpty()) {
                break;
            }
            Thread.sleep(200);
        }

        long count = outputBomIds == null ? 0 :
                outputBomIds.stream().filter(id -> bomId.equals(id)).count();
        assertThat(count).as("outputBomIds should contain bomId exactly once").isEqualTo(1);
    }

    // CS3: bom.released with null ecoId → handler skips, no error
    @Test
    void bomReleasedWithNullEcoIdIsSkipped() throws InterruptedException {
        String bomId = UUID.randomUUID().toString();
        Map<String, Object> event = new java.util.HashMap<>();
        event.put("eventType", "bom.released");
        event.put("bomId", bomId);
        event.put("ecoId", null);
        event.put("orgId", ORG_ID);
        event.put("parentItemId", UUID.randomUUID().toString());
        event.put("bomRevision", "REV-C");

        kafkaTemplate.send("bom.released", bomId, event);
        // Allow time for consumer to process — no exception expected
        Thread.sleep(500);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String engineerToken() {
        return buildToken(ORG_ID, List.of("ENGINEER"));
    }

    private HttpEntity<?> bearerRequest(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private String createEco(String token, String title) {
        ResponseEntity<Map> response = restTemplate.exchange(
                ECO_BASE, HttpMethod.POST,
                jsonRequest(token, Map.of(
                        "title", title,
                        "affectedItemIds", List.of(UUID.randomUUID().toString()))),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").toString();
    }
}
