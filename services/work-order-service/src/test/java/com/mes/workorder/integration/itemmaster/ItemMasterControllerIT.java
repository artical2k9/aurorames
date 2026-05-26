package com.mes.workorder.integration.itemmaster;

import com.mes.workorder.integration.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ItemMasterControllerIT extends BaseIntegrationTest {

    static final String ORG_ID = "00000000-0000-0000-0000-000000000001";
    static final String BASE_URL = "/api/v1/item-master";

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void postCreatesRecordAndReturns201WithLocationHeader() {
        String token = engineerToken();
        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL,
                HttpMethod.POST,
                jsonRequest(token, createRequest("BRKT-001", "A")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
    }

    @Test
    void postDuplicatePartNumberRevisionReturns409() {
        String token = engineerToken();
        Map<String, Object> request = createRequest("BRKT-DUPE", "A");

        restTemplate.exchange(BASE_URL, HttpMethod.POST, jsonRequest(token, request), Map.class);
        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL, HttpMethod.POST, jsonRequest(token, request), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void patchDescriptionReturns200WithModifiedFields() {
        String token = engineerToken();
        ResponseEntity<Map> created = restTemplate.exchange(
                BASE_URL, HttpMethod.POST, jsonRequest(token, createRequest("BRKT-PATCH", "A")), Map.class);
        String location = created.getHeaders().getLocation().getPath();
        String itemId = location.substring(location.lastIndexOf('/') + 1);

        ResponseEntity<Map> patched = restTemplate.exchange(
                BASE_URL + "/" + itemId,
                HttpMethod.PATCH,
                jsonRequest(token, Map.of("description", "Updated description")),
                Map.class);

        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patched.getBody()).extractingByKey("description").isEqualTo("Updated description");
    }

    @Test
    void getUnauthenticatedReturns401() {
        ResponseEntity<Map> response = restTemplate.getForEntity(BASE_URL, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getWithEngineerTokenReturns200() {
        String token = engineerToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shelfLifeControlledWithoutShelfLifeDaysReturns422() {
        String token = engineerToken();
        Map<String, Object> request = createRequest("BRKT-SL", "A");
        request.put("shelfLifeControlled", true);

        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL, HttpMethod.POST, jsonRequest(token, request), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void createAndPatchWritesTwoEnversAuditRows() {
        String token = engineerToken();
        ResponseEntity<Map> created = restTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                jsonRequest(token, createRequest("BRKT-AUD", "A")), Map.class);
        String location = created.getHeaders().getLocation().getPath();
        String itemId = location.substring(location.lastIndexOf('/') + 1);

        restTemplate.exchange(
                BASE_URL + "/" + itemId, HttpMethod.PATCH,
                jsonRequest(token, Map.of("description", "Audited update")), Map.class);

        Integer auditRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM work_order.item_master_aud WHERE id = ?::uuid",
                Integer.class, itemId);
        assertThat(auditRows).isEqualTo(2);
    }

    @Test
    void postWithNoPrivilegesReturns403() {
        if (!KEYCLOAK.isRunning()) {
            return;
        }
        String username = "viewer-" + UUID.randomUUID();
        KEYCLOAK.createUser(username, "pass", ORG_ID, java.util.List.of());
        String token = KEYCLOAK.fetchToken(username, "pass");

        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                jsonRequest(token, createRequest("BRKT-403", "A")), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String engineerToken() {
        if (!KEYCLOAK.isRunning()) {
            return "test-engineer-token";
        }
        String username = "engineer-" + UUID.randomUUID();
        KEYCLOAK.createUser(username, "pass", ORG_ID, java.util.List.of("ENGINEER"));
        return KEYCLOAK.fetchToken(username, "pass");
    }

    private Map<String, Object> createRequest(String partNumber, String revision) {
        return new java.util.HashMap<>(Map.of(
                "partNumber", partNumber,
                "revision", revision,
                "description", "Aluminium bracket",
                "unitOfMeasure", "EA",
                "cageCode", "CAGE01",
                "classification", "FABRICATED",
                "makeBuyCode", "MAKE",
                "traceabilityMethod", "SERIAL"
        ));
    }

    private HttpEntity<Map<String, Object>> jsonRequest(String token, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }
}
