package com.mes.quality.integration.inspectionplan;

import com.mes.quality.integration.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class InspectionPlanLifecycleIT extends BaseIntegrationTest {

    private static final String ORG_ID = "11111111-1111-1111-1111-111111111111";
    private static final String PLANS = "/api/v1/inspection-plans";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetStubs() {
        INVENTORY_WIREMOCK.resetAll();
    }

    private String adminToken() {
        return buildToken(ORG_ID, List.of("SYSTEM_ADMIN"));
    }

    @SuppressWarnings("unchecked")
    private String createPlan(String itemId, String partNumber) {
        INVENTORY_WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/item-master/" + itemId))
                .willReturn(okJson("{\"id\":\"" + itemId + "\",\"partNumber\":\"" + partNumber + "\"}")));
        ResponseEntity<Map> response = restTemplate.exchange(
                PLANS, HttpMethod.POST,
                jsonRequest(adminToken(), Map.of("itemId", itemId, "name", "Plan " + partNumber)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").toString();
    }

    @SuppressWarnings("unchecked")
    private void addCharacteristic(String planId) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("characteristicNumber", 10);
        body.put("name", "Bore");
        body.put("source", "DESIGN");
        body.put("characteristicType", "SPECIFIC");
        body.put("sampleSizeRule", "ALL");
        body.put("nominalValue", 10.0);
        restTemplate.exchange(PLANS + "/" + planId + "/characteristics", HttpMethod.POST,
                jsonRequest(adminToken(), body), Map.class);
    }

    @Test
    void deleteNeverApprovedReturns204() {
        String planId = createPlan(UUID.randomUUID().toString(), "PN-D1");
        ResponseEntity<Void> response = restTemplate.exchange(
                PLANS + "/" + planId, HttpMethod.DELETE,
                jsonRequest(adminToken(), Map.of()), Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @SuppressWarnings("unchecked")
    @Test
    void deleteEverApprovedReturns409() {
        String planId = createPlan(UUID.randomUUID().toString(), "PN-D2");
        addCharacteristic(planId);
        restTemplate.exchange(PLANS + "/" + planId + "/submit", HttpMethod.POST,
                jsonRequest(adminToken(), Map.of()), Map.class);
        restTemplate.exchange(PLANS + "/" + planId + "/approve", HttpMethod.POST,
                jsonRequest(adminToken(), Map.of()), Map.class);

        ResponseEntity<Map> response = restTemplate.exchange(
                PLANS + "/" + planId, HttpMethod.DELETE,
                jsonRequest(adminToken(), Map.of()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @SuppressWarnings("unchecked")
    @Test
    void mutationsWriteEnversAuditRows() {
        String planId = createPlan(UUID.randomUUID().toString(), "PN-AUD");
        addCharacteristic(planId);
        restTemplate.exchange(PLANS + "/" + planId + "/submit", HttpMethod.POST,
                jsonRequest(adminToken(), Map.of()), Map.class);
        restTemplate.exchange(PLANS + "/" + planId + "/approve", HttpMethod.POST,
                jsonRequest(adminToken(), Map.of()), Map.class);

        Integer planAudRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quality.inspection_plan_aud WHERE id = ?::uuid",
                Integer.class, planId);
        Integer revisionAudRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quality.inspection_plan_revision_aud r "
                        + "JOIN quality.inspection_plan_aud p ON p.id = r.inspection_plan_id "
                        + "WHERE p.id = ?::uuid",
                Integer.class, planId);

        assertThat(planAudRows).isPositive();
        // create (DRAFT) + submit (PENDING) + approve (APPROVED) → ≥3 revision audit rows.
        assertThat(revisionAudRows).isGreaterThanOrEqualTo(3);
    }
}
