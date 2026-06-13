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

class InspectionPlanApprovalGateIT extends BaseIntegrationTest {

    private static final String ORG_ID = "11111111-1111-1111-1111-111111111111";
    private static final String PLANS = "/api/v1/inspection-plans";

    @BeforeEach
    void resetStubs() {
        INVENTORY_WIREMOCK.resetAll();
    }

    private String adminToken() {
        return buildToken(ORG_ID, List.of("SYSTEM_ADMIN"));
    }

    @SuppressWarnings("unchecked")
    private String createPlan() {
        String itemId = UUID.randomUUID().toString();
        INVENTORY_WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/item-master/" + itemId))
                .willReturn(okJson("{\"id\":\"" + itemId + "\",\"partNumber\":\"PN-GATE\"}")));
        ResponseEntity<Map> response = restTemplate.exchange(
                PLANS, HttpMethod.POST,
                jsonRequest(adminToken(), Map.of("itemId", itemId, "name", "Gate plan")),
                Map.class);
        return response.getBody().get("id").toString();
    }

    @SuppressWarnings("unchecked")
    private void addSpecific(String planId, int number) {
        Map<String, Object> body = new HashMap<>();
        body.put("characteristicNumber", number);
        body.put("name", "Char " + number);
        body.put("source", "DESIGN");
        body.put("characteristicType", "SPECIFIC");
        body.put("sampleSizeRule", "ALL");
        body.put("nominalValue", 10.0);
        restTemplate.exchange(PLANS + "/" + planId + "/characteristics", HttpMethod.POST,
                jsonRequest(adminToken(), body), Map.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    void submitWithZeroCharacteristicsReturns422() {
        String planId = createPlan();
        ResponseEntity<Map> response = restTemplate.exchange(
                PLANS + "/" + planId + "/submit", HttpMethod.POST,
                jsonRequest(adminToken(), Map.of()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @SuppressWarnings("unchecked")
    @Test
    void submitWithCharacteristicsThenApproveSucceeds() {
        String planId = createPlan();
        addSpecific(planId, 10);

        ResponseEntity<Map> submitted = restTemplate.exchange(
                PLANS + "/" + planId + "/submit", HttpMethod.POST,
                jsonRequest(adminToken(), Map.of()), Map.class);
        assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(submitted.getBody().get("revisionStatus")).isEqualTo("PENDING_APPROVAL");

        ResponseEntity<Map> approved = restTemplate.exchange(
                PLANS + "/" + planId + "/approve", HttpMethod.POST,
                jsonRequest(adminToken(), Map.of()), Map.class);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approved.getBody().get("revisionStatus")).isEqualTo("APPROVED");
    }
}
