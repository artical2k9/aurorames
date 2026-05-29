package com.mes.workorder.integration.eco;

import com.mes.workorder.integration.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EcoControllerIT extends BaseIntegrationTest {

    static final String ORG_ID = "00000000-0000-0000-0000-000000000001";
    static final String ECO_BASE = "/api/v1/ecos";
    static final String ITEM_BASE = "/api/v1/item-master";
    static final String BOM_BASE = "/api/v1/boms";

    // AS1: create draft → 201
    @Test
    void createEcoReturns201WithDraftStatus() {
        String token = engineerToken();
        String itemId = createItem(token, "ECO-ITEM-001", "A");

        ResponseEntity<Map> response = restTemplate.exchange(
                ECO_BASE, HttpMethod.POST,
                jsonRequest(token, Map.of(
                        "title", "Test ECO",
                        "description", "Phase 1 change",
                        "affectedItemIds", List.of(itemId))),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).extractingByKey("status").isEqualTo("DRAFT");
        assertThat(response.getBody()).extractingByKey("id").isNotNull();
        assertThat(response.getBody()).extractingByKey("ecoNumber").isNotNull();
    }

    // AS2: approve → 200 + approvedBy set
    @Test
    void approveEcoReturns200WithApprovedBySet() {
        String token = engineerToken();
        String itemId = createItem(token, "ECO-APPR-ITEM-001", "A");
        String ecoId = createEco(token, "Approval Test ECO", itemId);

        ResponseEntity<Map> response = restTemplate.exchange(
                ECO_BASE + "/" + ecoId + "/approve", HttpMethod.POST,
                bearerRequest(token), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extractingByKey("status").isEqualTo("APPROVED");
        assertThat(response.getBody().get("approvedBy")).isNotNull();
    }

    // AS3: new BOM with ecoId → ECO outputBomIds updated (after release)
    @Test
    @SuppressWarnings("unchecked")
    void releasedBomWithEcoIdAppearsInOutputBomIds() {
        String token = engineerToken();
        String itemId = createItem(token, "ECO-BOM-ITEM-001", "A");
        String ecoId = createEco(token, "BOM Output ECO", itemId);

        // Create BOM referencing the ECO
        ResponseEntity<Map> bomResp = restTemplate.exchange(
                BOM_BASE, HttpMethod.POST,
                jsonRequest(token, Map.of(
                        "parentItemId", itemId,
                        "bomRevision", "REV-A",
                        "ecoId", ecoId)),
                Map.class);
        assertThat(bomResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String bomId = bomResp.getBody().get("id").toString();

        // Release the BOM → addOutputBom called
        restTemplate.exchange(BOM_BASE + "/" + bomId + "/release", HttpMethod.POST,
                bearerRequest(token), Map.class);

        // GET ECO → outputBomIds contains released BOM
        ResponseEntity<Map> ecoResp = restTemplate.exchange(
                ECO_BASE + "/" + ecoId, HttpMethod.GET,
                bearerRequest(token), Map.class);

        assertThat(ecoResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Object> outputBomIds = (List<Object>) ecoResp.getBody().get("outputBomIds");
        assertThat(outputBomIds).contains(bomId);
    }

    // AS4: concurrent ECO for same item → 201 with concurrentEcoWarning=true
    @Test
    void concurrentEcoForSameItemReturnsConcurrentWarningTrue() {
        String token = engineerToken();
        String itemId = createItem(token, "ECO-CONC-ITEM-001", "A");
        createEco(token, "First ECO", itemId);

        ResponseEntity<Map> secondEco = restTemplate.exchange(
                ECO_BASE, HttpMethod.POST,
                jsonRequest(token, Map.of(
                        "title", "Second ECO",
                        "affectedItemIds", List.of(itemId))),
                Map.class);

        assertThat(secondEco.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(secondEco.getBody()).extractingByKey("concurrentEcoWarning").isEqualTo(Boolean.TRUE);
    }

    // AS5: edit APPROVED ECO → 409
    @Test
    void approveAlreadyApprovedEcoReturns409() {
        String token = engineerToken();
        String itemId = createItem(token, "ECO-GUARD-ITEM-001", "A");
        String ecoId = createEco(token, "Guard ECO", itemId);

        restTemplate.exchange(ECO_BASE + "/" + ecoId + "/approve", HttpMethod.POST,
                bearerRequest(token), Map.class);

        ResponseEntity<Map> response = restTemplate.exchange(
                ECO_BASE + "/" + ecoId + "/approve", HttpMethod.POST,
                bearerRequest(token), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
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

    private String createItem(String token, String partNumber, String revision) {
        ResponseEntity<Map> response = restTemplate.exchange(
                ITEM_BASE, HttpMethod.POST,
                jsonRequest(token, baseItemRequest(partNumber, revision)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String path = response.getHeaders().getLocation().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private String createEco(String token, String title, String... itemIds) {
        ResponseEntity<Map> response = restTemplate.exchange(
                ECO_BASE, HttpMethod.POST,
                jsonRequest(token, Map.of(
                        "title", title,
                        "affectedItemIds", List.of(itemIds))),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").toString();
    }
}
