package com.mes.inventory.integration.bom;

import com.mes.inventory.integration.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MES-114 Phase 7: BOM revision history endpoint tests.
 * Covers GET /api/v1/boms/{bomId}/revisions.
 * Extends BaseIntegrationTest directly to avoid inheriting BomControllerIT @Test methods.
 */
class BomRevisionIT extends BaseIntegrationTest {

    private static final String ORG_ID   = "00000000-0000-0000-0000-000000000001";
    private static final String BOM_BASE  = "/api/v1/boms";
    private static final String ITEM_BASE = "/api/v1/item-master";

    @Test
    void listBomRevisions_returnsAllRevisionsOrderedAsc() {
        String eng   = engineerToken();
        String admin = sysAdminToken();
        Map<?, ?> parent = createItemBody(eng, "BOM-REV-HIST-001");
        String bomId = createBom(eng, itemId(parent));

        approveBom(eng, admin, bomId);

        restTemplate.exchange(BOM_BASE + "/" + bomId, HttpMethod.PATCH,
                jsonRequest(eng, Map.of("description", "Rev 1 description")), Map.class);

        ResponseEntity<List> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/revisions",
                HttpMethod.GET, bearerRequest(eng), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> revisions = response.getBody();
        assertThat(revisions).hasSize(2);

        Map<?, ?> rev0 = (Map<?, ?>) revisions.get(0);
        assertThat(rev0.get("revision")).isEqualTo(0);
        assertThat(rev0.get("revisionStatus")).isEqualTo("APPROVED");
        assertThat(rev0.get("approvedBy")).isNotNull();
        assertThat(rev0.get("approvedAt")).isNotNull();
        assertThat(rev0.get("createdBy")).isNotNull();
        assertThat(rev0.get("createdAt")).isNotNull();
        assertThat(rev0.get("bomRevisionId")).isNotNull();

        Map<?, ?> rev1 = (Map<?, ?>) revisions.get(1);
        assertThat(rev1.get("revision")).isEqualTo(1);
        assertThat(rev1.get("revisionStatus")).isEqualTo("DRAFT");
    }

    @Test
    void listBomRevisions_singleDraftRevision_returnsOneEntry() {
        String eng   = engineerToken();
        Map<?, ?> parent = createItemBody(eng, "BOM-REV-SINGLE-001");
        String bomId = createBom(eng, itemId(parent));

        ResponseEntity<List> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/revisions",
                HttpMethod.GET, bearerRequest(eng), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        Map<?, ?> rev = (Map<?, ?>) response.getBody().get(0);
        assertThat(rev.get("revision")).isEqualTo(0);
        assertThat(rev.get("revisionStatus")).isEqualTo("DRAFT");
    }

    @Test
    void listBomRevisions_unauthenticated_returns401() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                BOM_BASE + "/00000000-0000-0000-0000-000000000099/revisions", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void listBomRevisions_unknownBom_returns404() {
        String eng = engineerToken();
        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE + "/00000000-0000-0000-0000-000000000099/revisions",
                HttpMethod.GET, bearerRequest(eng), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @SuppressWarnings("unchecked")
    void editingApprovedBom_copiesLinesIntoNewDraft() {
        String eng   = engineerToken();
        String admin = sysAdminToken();
        String parent = itemId(createItemBody(eng, "BOM-COPY-PARENT-001"));
        String compRev = revisionId(createItemBody(eng, "BOM-COPY-COMP-001"));
        String bomId = createBom(eng, parent);
        addLine(eng, bomId, compRev, "010");

        approveBom(eng, admin, bomId);

        // Editing an APPROVED BOM auto-creates draft revision 1 and copies the lines into it.
        ResponseEntity<Map> patch = restTemplate.exchange(BOM_BASE + "/" + bomId, HttpMethod.PATCH,
                jsonRequest(eng, Map.of("description", "Rev 1 description")), Map.class);
        assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);

        // New draft (revision 1) carries the copied line.
        ResponseEntity<List> draftLines = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines?revisionNumber=1",
                HttpMethod.GET, bearerRequest(eng), List.class);
        assertThat(draftLines.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(draftLines.getBody()).hasSize(1);
        assertThat(((Map<?, ?>) draftLines.getBody().get(0)).get("findNumber")).isEqualTo("010");

        // The approved revision 0 still has its original line.
        ResponseEntity<List> approvedLines = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines?revisionNumber=0",
                HttpMethod.GET, bearerRequest(eng), List.class);
        assertThat(approvedLines.getBody()).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getBom_byRevisionNumber_selectsThatRevision() {
        String eng   = engineerToken();
        String admin = sysAdminToken();
        String bomId = createBom(eng, itemId(createItemBody(eng, "BOM-GETREV-001")));
        approveBom(eng, admin, bomId);
        restTemplate.exchange(BOM_BASE + "/" + bomId, HttpMethod.PATCH,
                jsonRequest(eng, Map.of("description", "Rev 1")), Map.class);

        ResponseEntity<Map> rev0 = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "?revisionNumber=0",
                HttpMethod.GET, bearerRequest(eng), Map.class);
        assertThat(rev0.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rev0.getBody().get("revision")).isEqualTo(0);
        assertThat(rev0.getBody().get("revisionStatus")).isEqualTo("APPROVED");

        ResponseEntity<Map> rev1 = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "?revisionNumber=1",
                HttpMethod.GET, bearerRequest(eng), Map.class);
        assertThat(rev1.getBody().get("revision")).isEqualTo(1);
        assertThat(rev1.getBody().get("revisionStatus")).isEqualTo("DRAFT");
    }

    @Test
    void getBom_unknownRevisionNumber_returns404() {
        String eng = engineerToken();
        String bomId = createBom(eng, itemId(createItemBody(eng, "BOM-GETREV-404")));
        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "?revisionNumber=99",
                HttpMethod.GET, bearerRequest(eng), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getBomLines_unknownRevisionNumber_returns404() {
        String eng = engineerToken();
        String bomId = createBom(eng, itemId(createItemBody(eng, "BOM-LINES-404")));
        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines?revisionNumber=99",
                HttpMethod.GET, bearerRequest(eng), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @SuppressWarnings("unchecked")
    void bomLineCustomFields_persistOnCreateAndUpdate() {
        String eng    = engineerToken();
        String parent = itemId(createItemBody(eng, "BOM-CF-PARENT-001"));
        String compRev = revisionId(createItemBody(eng, "BOM-CF-COMP-001"));
        String bomId  = createBom(eng, parent);

        ResponseEntity<Map> created = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines", HttpMethod.POST,
                jsonRequest(eng, Map.of(
                        "componentItemRevisionId", compRev,
                        "quantity", 1.0,
                        "unitOfMeasure", "EA",
                        "findNumber", "010",
                        "customFields", Map.of("torque", "5Nm"))),
                Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String lineId = created.getBody().get("id").toString();
        assertThat((Map<String, Object>) created.getBody().get("customFields"))
                .containsEntry("torque", "5Nm");

        ResponseEntity<Map> updated = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines/" + lineId, HttpMethod.PATCH,
                jsonRequest(eng, Map.of("customFields", Map.of("torque", "7Nm"))), Map.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Map<String, Object>) updated.getBody().get("customFields"))
                .containsEntry("torque", "7Nm");
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

    private void addLine(String token, String bomId, String componentRevisionId, String findNumber) {
        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines", HttpMethod.POST,
                jsonRequest(token, Map.of(
                        "componentItemRevisionId", componentRevisionId,
                        "quantity", 1.0,
                        "unitOfMeasure", "EA",
                        "findNumber", findNumber)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
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
}
