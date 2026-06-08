package com.mes.inventory.integration.itemmaster;

import com.mes.inventory.integration.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

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
                "SELECT COUNT(*) FROM inventory.item_master_aud WHERE id = ?::uuid",
                Integer.class, itemId);
        assertThat(auditRows).isEqualTo(2);
    }

    @Test
    void listWithSearch_returnsOnlyMatchingItems() {
        String token = engineerToken();
        restTemplate.exchange(BASE_URL, HttpMethod.POST,
                jsonRequest(token, createRequest("SRCH-MATCH-001", "A")), Map.class);
        restTemplate.exchange(BASE_URL, HttpMethod.POST,
                jsonRequest(token, createRequest("NOTSRCH-OTHER-001", "A")), Map.class);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL + "?search=SRCH-MATCH",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> content = (List<?>) response.getBody().get("content");
        assertThat(content).isNotEmpty();
        content.forEach(e -> {
            String pn = (String) ((Map<?, ?>) e).get("partNumber");
            assertThat(pn).containsIgnoringCase("SRCH-MATCH");
        });
    }

    @Test
    void listWithMakeBuyCode_returnsOnlyMatchingItems() {
        String token = engineerToken();
        Map<String, Object> buyReq = createRequest("MAKEBUY-BUY-001", "A");
        buyReq.put("makeBuyCode", "BUY");
        restTemplate.exchange(BASE_URL, HttpMethod.POST, jsonRequest(token, buyReq), Map.class);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL + "?makeBuyCode=BUY",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> content = (List<?>) response.getBody().get("content");
        assertThat(content).isNotEmpty();
        content.forEach(e -> assertThat(((Map<?, ?>) e).get("makeBuyCode")).isEqualTo("BUY"));
    }

    @Test
    void postWithNoPrivilegesReturns403() {
        String token = buildToken(ORG_ID, List.of());

        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                jsonRequest(token, createRequest("BRKT-403", "A")), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String engineerToken() {
        return buildToken(ORG_ID, List.of("ENGINEER"));
    }

    private Map<String, Object> createRequest(String partNumber, String revision) {
        return baseItemRequest(partNumber, revision);
    }
}
