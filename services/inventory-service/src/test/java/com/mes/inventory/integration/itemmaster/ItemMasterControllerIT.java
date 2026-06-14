package com.mes.inventory.integration.itemmaster;

import com.mes.inventory.integration.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
                BASE_URL, HttpMethod.POST,
                jsonRequest(token, createRequest("BRKT-001")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getBody()).extractingByKey("revisionStatus").isEqualTo("DRAFT");
        assertThat(response.getBody()).extractingByKey("revision").isEqualTo(0);
    }

    @Test
    void postDuplicatePartNumberReturns409() {
        String token = engineerToken();
        restTemplate.exchange(BASE_URL, HttpMethod.POST,
                jsonRequest(token, createRequest("BRKT-DUPE")), Map.class);
        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                jsonRequest(token, createRequest("BRKT-DUPE")), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void patchDescriptionOnDraftReturns200() {
        String token = engineerToken();
        String itemId = createItemAndGetId(token, "BRKT-PATCH");

        ResponseEntity<Map> patched = restTemplate.exchange(
                BASE_URL + "/" + itemId,
                HttpMethod.PATCH,
                jsonRequest(token, Map.of("description", "Updated description")),
                Map.class);

        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patched.getBody()).extractingByKey("description").isEqualTo("Updated description");
        assertThat(patched.getBody()).extractingByKey("revisionStatus").isEqualTo("DRAFT");
    }

    @Test
    void submitDraftReturns200WithPendingApprovalStatus() {
        String token = engineerToken();
        String itemId = createItemAndGetId(token, "BRKT-SUBMIT");

        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL + "/" + itemId + "/submit",
                HttpMethod.POST,
                bearerRequest(token),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extractingByKey("revisionStatus").isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void approveReturns200WithApprovedStatus() {
        String engineerToken = engineerToken();
        String adminToken = adminToken();
        String itemId = createItemAndGetId(engineerToken, "BRKT-APPROVE");
        submitDraft(engineerToken, itemId);

        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL + "/" + itemId + "/approve",
                HttpMethod.POST,
                bearerRequest(adminToken),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extractingByKey("revisionStatus").isEqualTo("APPROVED");
        assertThat(response.getBody().get("approvedBy")).isNotNull();
    }

    @Test
    void rejectWithReasonReturnsBackToDraft() {
        String engineerToken = engineerToken();
        String adminToken = adminToken();
        String itemId = createItemAndGetId(engineerToken, "BRKT-REJECT");
        submitDraft(engineerToken, itemId);

        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL + "/" + itemId + "/reject",
                HttpMethod.POST,
                jsonRequest(adminToken, Map.of("rejectionReason", "Missing cage code")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extractingByKey("revisionStatus").isEqualTo("DRAFT");
        assertThat(response.getBody()).extractingByKey("rejectionReason").isEqualTo("Missing cage code");
    }

    @Test
    void rejectWithEmptyReasonReturns422() {
        String engineerToken = engineerToken();
        String adminToken = adminToken();
        String itemId = createItemAndGetId(engineerToken, "BRKT-REJECT-EMPTY");
        submitDraft(engineerToken, itemId);

        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL + "/" + itemId + "/reject",
                HttpMethod.POST,
                jsonRequest(adminToken, Map.of("rejectionReason", "")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void patchApprovedItemAutoCreatesDraftAtNextRevision() {
        String engineerToken = engineerToken();
        String adminToken = adminToken();
        String itemId = createItemAndGetId(engineerToken, "BRKT-AUTOPATCH");
        submitDraft(engineerToken, itemId);
        approveDraft(adminToken, itemId);

        ResponseEntity<Map> patched = restTemplate.exchange(
                BASE_URL + "/" + itemId,
                HttpMethod.PATCH,
                jsonRequest(engineerToken, Map.of("description", "New revision")),
                Map.class);

        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patched.getBody()).extractingByKey("revisionStatus").isEqualTo("DRAFT");
        assertThat(patched.getBody()).extractingByKey("revision").isEqualTo(1);
        assertThat(patched.getBody()).extractingByKey("hasDraft").isEqualTo(true);
    }

    @Test
    void cancelDraftReturns204() {
        String token = engineerToken();
        String itemId = createItemAndGetId(token, "BRKT-CANCEL");

        ResponseEntity<Void> response = restTemplate.exchange(
                BASE_URL + "/" + itemId + "/draft",
                HttpMethod.DELETE,
                bearerRequest(token),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void getUnauthenticatedReturns401() {
        ResponseEntity<Map> response = restTemplate.getForEntity(BASE_URL, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getWithEngineerTokenReturns200() {
        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL, HttpMethod.GET, bearerRequest(engineerToken()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shelfLifeControlledWithoutShelfLifeDaysReturns422() {
        String token = engineerToken();
        Map<String, Object> request = createRequest("BRKT-SL");
        request.put("shelfLifeControlled", true);

        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL, HttpMethod.POST, jsonRequest(token, request), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void createAndPatchWritesTwoEnversAuditRows() {
        String token = engineerToken();
        String itemId = createItemAndGetId(token, "BRKT-AUD");

        restTemplate.exchange(
                BASE_URL + "/" + itemId, HttpMethod.PATCH,
                jsonRequest(token, Map.of("description", "Audited update")), Map.class);

        // revisionId is the UUID of the item_revision row — check item_revision_aud
        Map<String, Object> item = jdbcTemplate.queryForMap(
                "SELECT ir.id FROM inventory.item i " +
                "JOIN inventory.item_revision ir ON ir.item_id = i.id " +
                "WHERE i.id = ?::uuid LIMIT 1", itemId);
        String revisionId = item.get("id").toString();

        Integer auditRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory.item_revision_aud WHERE id = ?::uuid",
                Integer.class, revisionId);
        assertThat(auditRows).isEqualTo(2);
    }

    @Test
    void listWithSearch_returnsOnlyMatchingItems() {
        String token = engineerToken();
        createItemAndGetId(token, "SRCH-MATCH-001");
        createItemAndGetId(token, "NOTSRCH-OTHER-001");

        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL + "?search=SRCH-MATCH&size=500",
                HttpMethod.GET, bearerRequest(token), Map.class);

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
        Map<String, Object> buyReq = createRequest("MAKEBUY-BUY-001");
        buyReq.put("makeBuyCode", "BUY");
        restTemplate.exchange(BASE_URL, HttpMethod.POST, jsonRequest(token, buyReq), Map.class);

        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL + "?makeBuyCode=BUY&size=500",
                HttpMethod.GET, bearerRequest(token), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> content = (List<?>) response.getBody().get("content");
        assertThat(content).isNotEmpty();
        content.forEach(e -> assertThat(((Map<?, ?>) e).get("makeBuyCode")).isEqualTo("BUY"));
    }

    @Test
    void cloneFromApprovedSourceReturnsTemplate() {
        String engineerToken = engineerToken();
        String adminToken = adminToken();
        String itemId = createItemAndGetId(engineerToken, "CLONE-SRC-001");
        submitDraft(engineerToken, itemId);
        approveDraft(adminToken, itemId);

        ResponseEntity<Map> cloned = restTemplate.exchange(
                BASE_URL + "/" + itemId + "/clone",
                HttpMethod.POST, bearerRequest(engineerToken), Map.class);

        assertThat(cloned.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cloned.getBody().get("id")).isNull();
        assertThat(cloned.getBody().get("partNumber")).isNull();
        assertThat(cloned.getBody().get("revision")).isNull();
        assertThat(cloned.getBody().get("description")).isEqualTo("Test item");
    }

    @Test
    void cloneFromDraftOnlySourceReturns422() {
        String token = engineerToken();
        String itemId = createItemAndGetId(token, "CLONE-DRAFT-001");

        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL + "/" + itemId + "/clone",
                HttpMethod.POST, bearerRequest(token), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void postWithNoPrivilegesReturns403() {
        String token = buildToken(ORG_ID, List.of());

        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                jsonRequest(token, createRequest("BRKT-403")), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    String engineerToken() {
        return buildToken(ORG_ID, List.of("ENGINEER"));
    }

    String adminToken() {
        return buildToken(ORG_ID, List.of("SYSTEM_ADMIN"));
    }

    String createItemAndGetId(String token, String partNumber) {
        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                jsonRequest(token, createRequest(partNumber)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String path = response.getHeaders().getLocation().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    void submitDraft(String token, String itemId) {
        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL + "/" + itemId + "/submit",
                HttpMethod.POST, bearerRequest(token), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    void approveDraft(String token, String itemId) {
        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL + "/" + itemId + "/approve",
                HttpMethod.POST, bearerRequest(token), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private Map<String, Object> createRequest(String partNumber) {
        return baseItemRequest(partNumber, "IGNORED");
    }
}
