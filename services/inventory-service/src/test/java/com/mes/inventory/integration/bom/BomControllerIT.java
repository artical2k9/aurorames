package com.mes.inventory.integration.bom;

import com.mes.inventory.integration.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BomControllerIT extends BaseIntegrationTest {

    static final String ORG_ID = "00000000-0000-0000-0000-000000000001";
    static final String BOM_BASE = "/api/v1/boms";
    static final String ITEM_BASE = "/api/v1/item-master";

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void createBomReturns201WithDraftStatus() {
        String token = engineerToken();
        Map<?, ?> item = createItemBody(token, "BOM-HDR-001");

        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE, HttpMethod.POST,
                jsonRequest(token, Map.of("parentItemId", itemId(item))),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).extractingByKey("revisionStatus").isEqualTo("DRAFT");
        assertThat(response.getBody()).extractingByKey("id").isNotNull();
    }

    @Test
    void addBomLineReturns201() {
        String token = engineerToken();
        Map<?, ?> parent = createItemBody(token, "BOM-LN-PARENT-001");
        Map<?, ?> comp = createItemBody(token, "BOM-LN-COMP-001");
        String bomId = createBom(token, itemId(parent));

        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines", HttpMethod.POST,
                jsonRequest(token, Map.of(
                        "componentItemRevisionId", revisionId(comp),
                        "quantity", 2.0,
                        "unitOfMeasure", "EA",
                        "findNumber", "010")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).extractingByKey("findNumber").isEqualTo("010");
    }

    @Test
    void listBomLinesReturnsAllLines() {
        String token = engineerToken();
        Map<?, ?> parent = createItemBody(token, "BOM-LIST-PARENT-001");
        Map<?, ?> comp1 = createItemBody(token, "BOM-LIST-COMP-001");
        Map<?, ?> comp2 = createItemBody(token, "BOM-LIST-COMP-002");
        String bomId = createBom(token, itemId(parent));
        addLine(token, bomId, revisionId(comp1), "010");
        addLine(token, bomId, revisionId(comp2), "020");

        ResponseEntity<List> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines",
                HttpMethod.GET,
                bearerRequest(token),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    void approveBomReturns200WithApprovedStatus() {
        String token = engineerToken();
        String adminToken = sysAdminToken();
        Map<?, ?> parent = createItemBody(token, "BOM-APPR-PARENT-001");
        String bomId = createBom(token, itemId(parent));

        // submit → pending_approval
        ResponseEntity<Map> submitResp = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/submit", HttpMethod.POST,
                bearerRequest(token), Map.class);
        assertThat(submitResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(submitResp.getBody()).extractingByKey("revisionStatus").isEqualTo("PENDING_APPROVAL");

        // approve → approved
        ResponseEntity<Map> approveResp = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/approve", HttpMethod.POST,
                bearerRequest(adminToken), Map.class);
        assertThat(approveResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approveResp.getBody()).extractingByKey("revisionStatus").isEqualTo("APPROVED");
        assertThat(approveResp.getBody()).containsKey("approvedBy");
        assertThat(approveResp.getBody().get("approvedBy")).isNotNull();
    }

    @Test
    void addLineToApprovedBomReturns409() {
        String token = engineerToken();
        Map<?, ?> parent = createItemBody(token, "BOM-GUARD-PARENT-001");
        Map<?, ?> comp = createItemBody(token, "BOM-GUARD-COMP-001");
        String bomId = createBom(token, itemId(parent));
        approveBom(token, sysAdminToken(), bomId);

        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines", HttpMethod.POST,
                jsonRequest(token, Map.of(
                        "componentItemRevisionId", revisionId(comp),
                        "quantity", 1.0,
                        "unitOfMeasure", "EA",
                        "findNumber", "010")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void patchBomLineQuantityReturns200WithUpdatedQuantity() {
        String token = engineerToken();
        Map<?, ?> parent = createItemBody(token, "BOM-PATCH-PARENT-001");
        Map<?, ?> comp = createItemBody(token, "BOM-PATCH-COMP-001");
        String bomId = createBom(token, itemId(parent));
        String lineId = addLineAndGetId(token, bomId, revisionId(comp), "010");

        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines/" + lineId, HttpMethod.PATCH,
                jsonRequest(token, Map.of("quantity", 5.0)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("quantity").toString()).isEqualTo("5.0");
    }

    @Test
    void patchLineOnApprovedBomReturns409() {
        String token = engineerToken();
        Map<?, ?> parent = createItemBody(token, "BOM-PATCH-APPR-PARENT-001");
        Map<?, ?> comp = createItemBody(token, "BOM-PATCH-APPR-COMP-001");
        String bomId = createBom(token, itemId(parent));
        String lineId = addLineAndGetId(token, bomId, revisionId(comp), "010");
        approveBom(token, sysAdminToken(), bomId);

        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines/" + lineId, HttpMethod.PATCH,
                jsonRequest(token, Map.of("quantity", 3.0)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void listHeadersReturnsBomsForMultipleItems() {
        String token = engineerToken();
        Map<?, ?> item1 = createItemBody(token, "BOM-MULTI-001");
        Map<?, ?> item2 = createItemBody(token, "BOM-MULTI-002");
        createBom(token, itemId(item1));
        createBom(token, itemId(item2));

        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE + "/headers",
                HttpMethod.GET, bearerRequest(token), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("content");
        List<?> content = (List<?>) response.getBody().get("content");
        assertThat(content).isNotEmpty();
    }

    @Test
    void deleteLineReturns204AndLineIsGone() {
        String token = engineerToken();
        Map<?, ?> parent = createItemBody(token, "BOM-DEL-PARENT-001");
        Map<?, ?> comp = createItemBody(token, "BOM-DEL-COMP-001");
        String bomId = createBom(token, itemId(parent));
        String lineId = addLineAndGetId(token, bomId, revisionId(comp), "010");

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines/" + lineId,
                HttpMethod.DELETE, bearerRequest(token), Void.class);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<List> linesResponse = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines", HttpMethod.GET, bearerRequest(token), List.class);
        assertThat(linesResponse.getBody()).isEmpty();
    }

    @Test
    void patchHeaderReturns200WithUpdatedDescription() {
        String token = engineerToken();
        Map<?, ?> parent = createItemBody(token, "BOM-PHDR-PARENT-001");
        String bomId = createBom(token, itemId(parent));

        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId,
                HttpMethod.PATCH,
                jsonRequest(token, Map.of(
                        "description", "Updated description",
                        "reasonForRevision", "Design change",
                        "productionLine", "LINE-1",
                        "bomType", "MANUFACTURING",
                        "effectivityType", "DATE")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extractingByKey("description").isEqualTo("Updated description");
        assertThat(response.getBody()).extractingByKey("reasonForRevision").isEqualTo("Design change");
        assertThat(response.getBody()).extractingByKey("bomType").isEqualTo("MANUFACTURING");
    }

    @Test
    void downloadCsvReturns200WithCsvContentType() {
        String token = engineerToken();
        Map<?, ?> parent = createItemBody(token, "BOM-CSV-PARENT-001");
        String bomId = createBom(token, itemId(parent));

        ResponseEntity<byte[]> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/explosion/download?format=flat&download=csv",
                HttpMethod.GET, bearerRequest(token), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).contains("text/csv");
        assertThat(new String(response.getBody())).contains("Find #");
    }

    @Test
    void downloadPdfReturns200WithPdfContentType() {
        String token = engineerToken();
        Map<?, ?> parent = createItemBody(token, "BOM-PDF-PARENT-001");
        String bomId = createBom(token, itemId(parent));

        ResponseEntity<byte[]> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/explosion/download?format=flat&download=pdf",
                HttpMethod.GET, bearerRequest(token), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).contains("application/pdf");
        assertThat(response.getBody()).startsWith(new byte[]{'%', 'P', 'D', 'F'});
    }

    @Test
    void listHeaders_returns200WithItemDetails() {
        String token = engineerToken();
        Map<?, ?> parent = createItemBody(token, "BOM-HDR-LIST-001");
        createBom(token, itemId(parent));

        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE + "/headers",
                HttpMethod.GET,
                bearerRequest(token),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("content");
        List<?> content = (List<?>) response.getBody().get("content");
        assertThat(content).isNotEmpty();
        Map<?, ?> first = (Map<?, ?>) content.stream()
                .filter(e -> "BOM-HDR-LIST-001".equals(((Map<?, ?>) e).get("partNumber")))
                .findFirst().orElse(null);
        assertThat(first).isNotNull();
        assertThat(first.get("revision")).isNotNull();
        assertThat(first.get("partNumber")).isEqualTo("BOM-HDR-LIST-001");
        assertThat(first.get("revisionStatus")).isNotNull();
    }

    @Test
    void listHeaders_withSearch_returnsMatchingBomsOnly() {
        String token = engineerToken();
        Map<?, ?> matchItem = createItemBody(token, "BOM-SRCH-UNIQUE-XYZ");
        createItemBody(token, "BOM-SRCH-OTHER-001");
        createBom(token, itemId(matchItem));

        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE + "/headers?search=SRCH-UNIQUE",
                HttpMethod.GET,
                bearerRequest(token),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> content = (List<?>) response.getBody().get("content");
        assertThat(content).isNotEmpty();
        content.forEach(e -> {
            String pn = (String) ((Map<?, ?>) e).get("partNumber");
            assertThat(pn).containsIgnoringCase("SRCH-UNIQUE");
        });
    }

    @Test
    void listHeaders_unauthenticated_returns401() {
        ResponseEntity<Map> response = restTemplate.getForEntity(BOM_BASE + "/headers", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void addLineWithNonExistentComponentReturns422() {
        String token = engineerToken();
        Map<?, ?> parent = createItemBody(token, "BOM-NF-PARENT-001");
        String bomId = createBom(token, itemId(parent));

        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines", HttpMethod.POST,
                jsonRequest(token, Map.of(
                        "componentItemRevisionId", UUID.randomUUID().toString(),
                        "quantity", 1.0,
                        "unitOfMeasure", "EA",
                        "findNumber", "010")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void approveBomWritesEnversAuditRow() {
        String token = engineerToken();
        Map<?, ?> parent = createItemBody(token, "BOM-AUD-PARENT-001");
        String bomId = createBom(token, itemId(parent));
        approveBom(token, sysAdminToken(), bomId);

        // bom_revision_aud should have an entry for the approved revision
        Integer auditRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory.bom_revision_aud br"
                + " JOIN inventory.bom b ON b.id = br.bom_id"
                + " WHERE b.id = ?::uuid",
                Integer.class, bomId);
        assertThat(auditRows).isGreaterThanOrEqualTo(1);
    }

    @Test
    void updateLineDelegatesToEnrichLineAndReturnsComponentDetails() {
        String token = engineerToken();
        Map<?, ?> parent = createItemBody(token, "BOM-ENRICH-PARENT-001");
        Map<?, ?> comp = createItemBody(token, "BOM-ENRICH-COMP-001");
        String bomId = createBom(token, itemId(parent));
        String lineId = addLineAndGetId(token, bomId, revisionId(comp), "010");

        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines/" + lineId, HttpMethod.PATCH,
                jsonRequest(token, Map.of("quantity", 3.0)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("partNumber");
        assertThat(response.getBody().get("partNumber")).isEqualTo("BOM-ENRICH-COMP-001");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String engineerToken() {
        return buildToken(ORG_ID, List.of("ENGINEER"));
    }

    private String sysAdminToken() {
        return buildToken(ORG_ID, List.of("SYSTEM_ADMIN"));
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> createItemBody(String token, String partNumber) {
        ResponseEntity<Map> response = restTemplate.exchange(
                ITEM_BASE, HttpMethod.POST,
                jsonRequest(token, baseItemRequest(partNumber, "A")), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private String itemId(Map<?, ?> itemBody) {
        return itemBody.get("id").toString();
    }

    private String revisionId(Map<?, ?> itemBody) {
        return itemBody.get("revisionId").toString();
    }

    @SuppressWarnings("unchecked")
    private String createBom(String token, String parentItemId) {
        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE, HttpMethod.POST,
                jsonRequest(token, Map.of("parentItemId", parentItemId)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").toString();
    }

    private void approveBom(String token, String adminToken, String bomId) {
        ResponseEntity<Map> submitResp = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/submit", HttpMethod.POST,
                bearerRequest(token), Map.class);
        assertThat(submitResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> approveResp = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/approve", HttpMethod.POST,
                bearerRequest(adminToken), Map.class);
        assertThat(approveResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @SuppressWarnings("unchecked")
    private String addLineAndGetId(String token, String bomId, String componentRevisionId,
                                   String findNumber) {
        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines", HttpMethod.POST,
                jsonRequest(token, Map.of(
                        "componentItemRevisionId", componentRevisionId,
                        "quantity", 1.0,
                        "unitOfMeasure", "EA",
                        "findNumber", findNumber)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").toString();
    }

    private void addLine(String token, String bomId, String componentRevisionId, String findNumber) {
        addLineAndGetId(token, bomId, componentRevisionId, findNumber);
    }
}
