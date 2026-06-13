package com.mes.quality.integration.inspectionplan;

import com.mes.quality.integration.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class ConsumerContractIT extends BaseIntegrationTest {

    private static final String ORG_ID = "11111111-1111-1111-1111-111111111111";
    private static final String PLANS = "/api/v1/inspection-plans";
    private static final String BY_ITEM = "/api/v1/inspection-plans/by-item/";

    @BeforeEach
    void resetStubs() {
        INVENTORY_WIREMOCK.resetAll();
    }

    private String adminToken() {
        return buildToken(ORG_ID, List.of("SYSTEM_ADMIN"));
    }

    @SuppressWarnings("unchecked")
    private String createPlan(String itemId) {
        INVENTORY_WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/item-master/" + itemId))
                .willReturn(okJson("{\"id\":\"" + itemId + "\",\"partNumber\":\"PN-CONS\"}")));
        ResponseEntity<Map> response = restTemplate.exchange(
                PLANS, HttpMethod.POST,
                jsonRequest(adminToken(), Map.of("itemId", itemId, "name", "Consumer plan")),
                Map.class);
        return response.getBody().get("id").toString();
    }

    @SuppressWarnings("unchecked")
    private void addAndApprove(String planId) {
        Map<String, Object> body = new HashMap<>();
        body.put("characteristicNumber", 10);
        body.put("name", "Bore");
        body.put("source", "DESIGN");
        body.put("characteristicType", "SPECIFIC");
        body.put("sampleSizeRule", "ALL");
        body.put("nominalValue", 10.0);
        restTemplate.exchange(PLANS + "/" + planId + "/characteristics", HttpMethod.POST,
                jsonRequest(adminToken(), body), Map.class);
        restTemplate.exchange(PLANS + "/" + planId + "/submit", HttpMethod.POST,
                jsonRequest(adminToken(), Map.of()), Map.class);
        restTemplate.exchange(PLANS + "/" + planId + "/approve", HttpMethod.POST,
                jsonRequest(adminToken(), Map.of()), Map.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    void approvedReturns404ForDraftOnlyPlan() {
        String itemId = UUID.randomUUID().toString();
        createPlan(itemId);
        ResponseEntity<Map> response = restTemplate.exchange(
                BY_ITEM + itemId + "/approved", HttpMethod.GET,
                jsonRequest(adminToken(), Map.of()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("error").toString()).contains("NO_APPROVED_PLAN");
    }

    @SuppressWarnings("unchecked")
    @Test
    void approvedReturnsLatestApprovedWithCharacteristics() {
        String itemId = UUID.randomUUID().toString();
        String planId = createPlan(itemId);
        addAndApprove(planId);

        ResponseEntity<Map> response = restTemplate.exchange(
                BY_ITEM + itemId + "/approved", HttpMethod.GET,
                jsonRequest(adminToken(), Map.of()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("planId")).isEqualTo(planId);
        assertThat(response.getBody().get("revision")).isEqualTo(0);
        assertThat((List<?>) response.getBody().get("characteristics")).hasSize(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    void statusReflectsLifecycle() {
        String itemId = UUID.randomUUID().toString();

        // Unknown item → exists=false.
        ResponseEntity<Map> none = restTemplate.exchange(
                BY_ITEM + itemId + "/status", HttpMethod.GET,
                jsonRequest(adminToken(), Map.of()), Map.class);
        assertThat(none.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(none.getBody().get("exists")).isEqualTo(false);
        assertThat(none.getBody().get("approved")).isEqualTo(false);

        // Draft-only plan → exists=true, approved=false.
        String planId = createPlan(itemId);
        ResponseEntity<Map> draft = restTemplate.exchange(
                BY_ITEM + itemId + "/status", HttpMethod.GET,
                jsonRequest(adminToken(), Map.of()), Map.class);
        assertThat(draft.getBody().get("exists")).isEqualTo(true);
        assertThat(draft.getBody().get("approved")).isEqualTo(false);

        // Approved → approved=true, latestApprovedRevision=0.
        addAndApprove(planId);
        ResponseEntity<Map> approved = restTemplate.exchange(
                BY_ITEM + itemId + "/status", HttpMethod.GET,
                jsonRequest(adminToken(), Map.of()), Map.class);
        assertThat(approved.getBody().get("approved")).isEqualTo(true);
        assertThat(approved.getBody().get("latestApprovedRevision")).isEqualTo(0);
    }
}
