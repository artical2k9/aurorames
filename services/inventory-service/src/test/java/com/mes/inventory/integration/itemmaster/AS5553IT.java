package com.mes.inventory.integration.itemmaster;

import com.mes.inventory.integration.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-016a — AS5553 counterfeit risk fields are managed exclusively via
 * PATCH /item-master/{id}/as5553, which requires item-master:as5553:manage.
 * ENGINEER does not hold this privilege and must receive 403.
 */
class AS5553IT extends BaseIntegrationTest {

    static final String ORG_ID  = "00000000-0000-0000-0000-000000000001";
    static final String BASE_URL = "/api/v1/item-master";

    // ── T192 / FR-016a: QUALITY_ENGINEER can set AS5553 fields ──────────────

    @Test
    void qualityEngineerPatchesAs5553FieldsReturns200() {
        String engineerToken = buildToken(ORG_ID, List.of("ENGINEER"));
        String qeToken = buildToken(ORG_ID, List.of("QUALITY_ENGINEER"));

        String itemId = createItem(engineerToken, "AS5553-QE-001", "A");

        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL + "/" + itemId + "/as5553",
                HttpMethod.PATCH,
                jsonRequest(qeToken, Map.of(
                        "counterfeitRiskLevel", "HIGH",
                        "approvedSuppliers",    List.of("Supplier-A", "Supplier-B"),
                        "verificationRequired", true)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extractingByKey("counterfeitRiskLevel").isEqualTo("HIGH");
        assertThat(response.getBody()).extractingByKey("verificationRequired").isEqualTo(true);
    }

    // ── T192 / FR-016a: ENGINEER is forbidden from AS5553 endpoint ──────────

    @Test
    void engineerPatchAs5553Returns403() {
        String engineerToken = buildToken(ORG_ID, List.of("ENGINEER"));
        String itemId = createItem(engineerToken, "AS5553-ENG-001", "A");

        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL + "/" + itemId + "/as5553",
                HttpMethod.PATCH,
                jsonRequest(engineerToken, Map.of("counterfeitRiskLevel", "LOW")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── T088: BOM explosion node sets counterfeitRiskAlert for HIGH-risk item ─

    @Test
    void listWithCounterfeitRiskLevelHighReturnsMatchingItem() {
        String engineerToken = buildToken(ORG_ID, List.of("ENGINEER"));
        String qeToken = buildToken(ORG_ID, List.of("QUALITY_ENGINEER"));

        String itemId = createItem(engineerToken, "AS5553-RISK-HIGH-001", "A");

        restTemplate.exchange(
                BASE_URL + "/" + itemId + "/as5553",
                HttpMethod.PATCH,
                jsonRequest(qeToken, Map.of("counterfeitRiskLevel", "HIGH")),
                Map.class);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(engineerToken);
        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL + "?counterfeitRiskLevel=HIGH",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> content = (List<?>) response.getBody().get("content");
        assertThat(content).isNotEmpty();
        boolean found = content.stream()
                .anyMatch(e -> "AS5553-RISK-HIGH-001".equals(((Map<?, ?>) e).get("partNumber")));
        assertThat(found).isTrue();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String createItem(String token, String partNumber, String revision) {
        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                jsonRequest(token, baseItemRequest(partNumber, revision)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String path = response.getHeaders().getLocation().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
