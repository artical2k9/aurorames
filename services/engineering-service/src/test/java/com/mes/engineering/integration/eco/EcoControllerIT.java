package com.mes.engineering.integration.eco;

import com.mes.engineering.integration.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EcoControllerIT extends BaseIntegrationTest {

    static final String ORG_ID = "00000000-0000-0000-0000-000000000001";
    static final String ECO_BASE = "/api/v1/ecos";

    // AS1: create draft → 201
    @Test
    void createEcoReturns201WithDraftStatus() {
        String token = engineerToken();

        ResponseEntity<Map> response = restTemplate.exchange(
                ECO_BASE, HttpMethod.POST,
                jsonRequest(token, Map.of(
                        "title", "Test ECO",
                        "description", "Phase 1 change",
                        "affectedItemIds", List.of(UUID.randomUUID().toString()))),
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
        String ecoId = createEco(token, "Approval Test ECO");

        ResponseEntity<Map> response = restTemplate.exchange(
                ECO_BASE + "/" + ecoId + "/approve", HttpMethod.POST,
                bearerRequest(token), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extractingByKey("status").isEqualTo("APPROVED");
        assertThat(response.getBody().get("approvedBy")).isNotNull();
    }

    // AS4: concurrent ECO for same item → 201 with concurrentEcoWarning=true
    @Test
    void concurrentEcoForSameItemReturnsConcurrentWarningTrue() {
        String token = engineerToken();
        String sharedItemId = UUID.randomUUID().toString();
        createEcoWithItem(token, "First ECO", sharedItemId);

        ResponseEntity<Map> secondEco = restTemplate.exchange(
                ECO_BASE, HttpMethod.POST,
                jsonRequest(token, Map.of(
                        "title", "Second ECO",
                        "affectedItemIds", List.of(sharedItemId))),
                Map.class);

        assertThat(secondEco.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(secondEco.getBody()).extractingByKey("concurrentEcoWarning").isEqualTo(Boolean.TRUE);
    }

    // AS5: approve already-approved ECO → 409
    @Test
    void approveAlreadyApprovedEcoReturns409() {
        String token = engineerToken();
        String ecoId = createEco(token, "Guard ECO");

        restTemplate.exchange(ECO_BASE + "/" + ecoId + "/approve", HttpMethod.POST,
                bearerRequest(token), Map.class);

        ResponseEntity<Map> response = restTemplate.exchange(
                ECO_BASE + "/" + ecoId + "/approve", HttpMethod.POST,
                bearerRequest(token), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // AS6: list ECOs returns paginated results
    @Test
    @SuppressWarnings("unchecked")
    void listEcosReturns200WithContent() {
        String token = engineerToken();
        createEco(token, "List ECO 1");
        createEco(token, "List ECO 2");

        ResponseEntity<Map> response = restTemplate.exchange(
                ECO_BASE + "?page=0&size=20", HttpMethod.GET,
                bearerRequest(token), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Object> content = (List<Object>) response.getBody().get("content");
        assertThat(content).isNotEmpty();
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
        return createEcoWithItem(token, title, UUID.randomUUID().toString());
    }

    private String createEcoWithItem(String token, String title, String itemId) {
        ResponseEntity<Map> response = restTemplate.exchange(
                ECO_BASE, HttpMethod.POST,
                jsonRequest(token, Map.of(
                        "title", title,
                        "affectedItemIds", List.of(itemId))),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").toString();
    }
}
