package com.mes.inventory.integration.bom;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mes.inventory.integration.BaseIntegrationTest;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class BomReleasedEventIT extends BaseIntegrationTest {

    static final String ORG_ID = "00000000-0000-0000-0000-000000000002";
    static final String BOM_BASE = "/api/v1/boms";
    static final String ITEM_BASE = "/api/v1/item-master";

    @Autowired
    BomReleasedCaptor captor;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void approveBomPublishesBomReleasedEventWithAllRequiredFields() throws Exception {
        String token = buildToken(ORG_ID, List.of("ENGINEER"));
        String adminToken = buildToken(ORG_ID, List.of("SYSTEM_ADMIN"));
        UUID ecoId = UUID.randomUUID();

        ResponseEntity<Map> itemResp = restTemplate.exchange(
                ITEM_BASE, HttpMethod.POST,
                jsonRequest(token, baseItemRequest("EVT-PARENT-001", "A")), Map.class);
        assertThat(itemResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String parentItemId = itemResp.getBody().get("id").toString();

        ResponseEntity<Map> bomResp = restTemplate.exchange(
                BOM_BASE, HttpMethod.POST,
                jsonRequest(token, Map.of(
                        "parentItemId", parentItemId,
                        "ecoId", ecoId.toString())),
                Map.class);
        assertThat(bomResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String bomId = bomResp.getBody().get("id").toString();

        // submit → approve
        restTemplate.exchange(BOM_BASE + "/" + bomId + "/submit",
                HttpMethod.POST, bearerRequest(token), Map.class);
        ResponseEntity<Map> approveResp = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/approve",
                HttpMethod.POST, bearerRequest(adminToken), Map.class);
        assertThat(approveResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<?, ?> payload = findEventForBom(bomId);
        assertThat(payload).as("bom.released event not received within 10s for bomId=%s", bomId)
                .isNotNull();
        assertThat(payload.get("bomId")).isEqualTo(bomId);
        assertThat(payload.get("ecoId")).isEqualTo(ecoId.toString());
        assertThat(payload.get("orgId")).isEqualTo(ORG_ID);
        assertThat(payload.get("parentItemId")).isEqualTo(parentItemId);
        assertThat(payload.get("revision")).isNotNull();
    }

    @Test
    void approveBomWithNullEcoIdPublishesEventWithNullEcoId() throws Exception {
        String token = buildToken(ORG_ID, List.of("ENGINEER"));
        String adminToken = buildToken(ORG_ID, List.of("SYSTEM_ADMIN"));

        ResponseEntity<Map> itemResp = restTemplate.exchange(
                ITEM_BASE, HttpMethod.POST,
                jsonRequest(token, baseItemRequest("EVT-NOECO-001", "A")), Map.class);
        String parentItemId = itemResp.getBody().get("id").toString();

        ResponseEntity<Map> bomResp = restTemplate.exchange(
                BOM_BASE, HttpMethod.POST,
                jsonRequest(token, Map.of("parentItemId", parentItemId)),
                Map.class);
        String bomId = bomResp.getBody().get("id").toString();

        restTemplate.exchange(BOM_BASE + "/" + bomId + "/submit",
                HttpMethod.POST, bearerRequest(token), Map.class);
        restTemplate.exchange(BOM_BASE + "/" + bomId + "/approve",
                HttpMethod.POST, bearerRequest(adminToken), Map.class);

        Map<?, ?> payload = findEventForBom(bomId);
        assertThat(payload).as("bom.released event not received within 10s for bomId=%s", bomId)
                .isNotNull();
        assertThat(payload.get("bomId")).isEqualTo(bomId);
        assertThat(payload.get("ecoId")).isNull();
        assertThat(payload.get("parentItemId")).isEqualTo(parentItemId);
    }

    private Map<?, ?> findEventForBom(String bomId) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecord<String, String> rec = captor.poll(500, TimeUnit.MILLISECONDS);
            if (rec != null) {
                Map<?, ?> payload = objectMapper.readValue(rec.value(), Map.class);
                if (bomId.equals(payload.get("bomId"))) {
                    return payload;
                }
            }
        }
        return null;
    }
}
