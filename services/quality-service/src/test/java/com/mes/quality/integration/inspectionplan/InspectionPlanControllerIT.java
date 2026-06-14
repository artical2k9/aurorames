package com.mes.quality.integration.inspectionplan;

import com.mes.quality.integration.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class InspectionPlanControllerIT extends BaseIntegrationTest {

    private static final String ORG_ID = "11111111-1111-1111-1111-111111111111";
    private static final String OTHER_ORG = "22222222-2222-2222-2222-222222222222";
    private static final String PLANS = "/api/v1/inspection-plans";

    @BeforeEach
    void resetStubs() {
        INVENTORY_WIREMOCK.resetAll();
    }

    private String adminToken() {
        return buildToken(ORG_ID, List.of("SYSTEM_ADMIN"));
    }

    private void stubItem(String itemId, String partNumber) {
        INVENTORY_WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/item-master/" + itemId))
                .willReturn(okJson("{\"id\":\"" + itemId + "\",\"partNumber\":\"" + partNumber + "\"}")));
    }

    @SuppressWarnings("unchecked")
    private String createPlan(String itemId, String partNumber, String name) {
        stubItem(itemId, partNumber);
        ResponseEntity<Map> response = restTemplate.exchange(
                PLANS, HttpMethod.POST,
                jsonRequest(adminToken(), Map.of("itemId", itemId, "name", name)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").toString();
    }

    /** Adds one SPECIFIC characteristic so the plan can pass the submit-gate. */
    @SuppressWarnings("unchecked")
    private void addCharacteristic(String planId) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("characteristicNumber", 10);
        body.put("name", "Bore");
        body.put("source", "DESIGN");
        body.put("characteristicType", "SPECIFIC");
        body.put("sampleSizeRule", "ALL");
        body.put("nominalValue", 10.0);
        ResponseEntity<Map> r = restTemplate.exchange(
                PLANS + "/" + planId + "/characteristics", HttpMethod.POST,
                jsonRequest(adminToken(), body), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @SuppressWarnings("unchecked")
    @Test
    void createReturnsRevisionZeroDraft() {
        String itemId = UUID.randomUUID().toString();
        stubItem(itemId, "PN-100");
        ResponseEntity<Map> response = restTemplate.exchange(
                PLANS, HttpMethod.POST,
                jsonRequest(adminToken(), Map.of("itemId", itemId, "name", "Bracket control plan")),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("revision")).isEqualTo(0);
        assertThat(response.getBody().get("revisionStatus")).isEqualTo("DRAFT");
        assertThat(response.getBody().get("partNumber")).isEqualTo("PN-100");
        assertThat(response.getBody().get("hasDraft")).isEqualTo(true);
    }

    @SuppressWarnings("unchecked")
    @Test
    void duplicatePlanForItemReturns409() {
        String itemId = UUID.randomUUID().toString();
        createPlan(itemId, "PN-101", "First");
        stubItem(itemId, "PN-101");
        ResponseEntity<Map> dup = restTemplate.exchange(
                PLANS, HttpMethod.POST,
                jsonRequest(adminToken(), Map.of("itemId", itemId, "name", "Second")),
                Map.class);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @SuppressWarnings("unchecked")
    @Test
    void unknownItemReturns422() {
        // No WireMock stub → inventory-service responds 404 → 422.
        ResponseEntity<Map> response = restTemplate.exchange(
                PLANS, HttpMethod.POST,
                jsonRequest(adminToken(), Map.of("itemId", UUID.randomUUID().toString(), "name", "Orphan")),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @SuppressWarnings("unchecked")
    @Test
    void submitApproveLifecycle() {
        String planId = createPlan(UUID.randomUUID().toString(), "PN-200", "Lifecycle plan");
        addCharacteristic(planId);

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
        assertThat(approved.getBody().get("approvedBy")).isNotNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    void rejectReturnsToDraftAndRequiresReason() {
        String planId = createPlan(UUID.randomUUID().toString(), "PN-201", "Reject plan");
        addCharacteristic(planId);
        restTemplate.exchange(PLANS + "/" + planId + "/submit", HttpMethod.POST,
                jsonRequest(adminToken(), Map.of()), Map.class);

        ResponseEntity<Map> noReason = restTemplate.exchange(
                PLANS + "/" + planId + "/reject", HttpMethod.POST,
                jsonRequest(adminToken(), Map.of()), Map.class);
        assertThat(noReason.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        ResponseEntity<Map> rejected = restTemplate.exchange(
                PLANS + "/" + planId + "/reject", HttpMethod.POST,
                jsonRequest(adminToken(), Map.of("reason", "Limits out of tolerance")), Map.class);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rejected.getBody().get("revisionStatus")).isEqualTo("DRAFT");
        assertThat(rejected.getBody().get("rejectionReason")).isEqualTo("Limits out of tolerance");
    }

    @SuppressWarnings("unchecked")
    @Test
    void patchOnApprovedAutoCreatesDraftAndEnforcesOneDraft() {
        String planId = createPlan(UUID.randomUUID().toString(), "PN-202", "Auto draft plan");
        addCharacteristic(planId);
        restTemplate.exchange(PLANS + "/" + planId + "/submit", HttpMethod.POST,
                jsonRequest(adminToken(), Map.of()), Map.class);
        restTemplate.exchange(PLANS + "/" + planId + "/approve", HttpMethod.POST,
                jsonRequest(adminToken(), Map.of()), Map.class);

        ResponseEntity<Map> patched = restTemplate.exchange(
                PLANS + "/" + planId, HttpMethod.PATCH,
                jsonRequest(adminToken(), Map.of("description", "Revised")), Map.class);
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patched.getBody().get("revision")).isEqualTo(1);
        assertThat(patched.getBody().get("revisionStatus")).isEqualTo("DRAFT");

        // Explicit Create Revision while a draft exists → 409 (one-draft invariant).
        ResponseEntity<Map> secondDraft = restTemplate.exchange(
                PLANS + "/" + planId + "/revisions", HttpMethod.POST,
                jsonRequest(adminToken(), Map.of()), Map.class);
        assertThat(secondDraft.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondDraft.getBody().get("revision")).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    void revisionHistoryOrderedAndDisplayResolution() {
        String planId = createPlan(UUID.randomUUID().toString(), "PN-203", "History plan");
        addCharacteristic(planId);
        restTemplate.exchange(PLANS + "/" + planId + "/submit", HttpMethod.POST,
                jsonRequest(adminToken(), Map.of()), Map.class);
        restTemplate.exchange(PLANS + "/" + planId + "/approve", HttpMethod.POST,
                jsonRequest(adminToken(), Map.of()), Map.class);
        restTemplate.exchange(PLANS + "/" + planId, HttpMethod.PATCH,
                jsonRequest(adminToken(), Map.of("description", "Rev1 draft")), Map.class);

        ResponseEntity<List> history = restTemplate.exchange(
                PLANS + "/" + planId + "/revisions", HttpMethod.GET,
                jsonRequest(adminToken(), Map.of()), List.class);
        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(history.getBody()).hasSize(2);

        // Default display revision = APPROVED (rev 0) even though a DRAFT rev 1 exists.
        ResponseEntity<Map> display = restTemplate.exchange(
                PLANS + "/" + planId, HttpMethod.GET,
                jsonRequest(adminToken(), Map.of()), Map.class);
        assertThat(display.getBody().get("revisionStatus")).isEqualTo("APPROVED");
        assertThat(display.getBody().get("hasDraft")).isEqualTo(true);
    }

    @SuppressWarnings("unchecked")
    @Test
    void unauthenticatedReturns401() {
        ResponseEntity<Map> response = restTemplate.exchange(
                PLANS, HttpMethod.GET, null, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @SuppressWarnings("unchecked")
    @Test
    void engineerCannotCreate403() {
        String itemId = UUID.randomUUID().toString();
        stubItem(itemId, "PN-300");
        ResponseEntity<Map> response = restTemplate.exchange(
                PLANS, HttpMethod.POST,
                jsonRequest(buildToken(ORG_ID, List.of("ENGINEER")),
                        Map.of("itemId", itemId, "name", "Blocked")),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @SuppressWarnings("unchecked")
    @Test
    void otherOrgCannotSeePlan404() {
        String planId = createPlan(UUID.randomUUID().toString(), "PN-301", "Org-scoped plan");
        ResponseEntity<Map> response = restTemplate.exchange(
                PLANS + "/" + planId, HttpMethod.GET,
                jsonRequest(buildToken(OTHER_ORG, List.of("SYSTEM_ADMIN")), Map.of()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
