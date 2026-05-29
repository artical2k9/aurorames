package com.mes.workorder.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditTrailIT extends BaseIntegrationTest {

    static final String ORG_ID = "00000000-0000-0000-0000-000000000001";
    static final String ITEM_BASE = "/api/v1/item-master";
    static final String BOM_BASE = "/api/v1/boms";
    static final String ECO_BASE = "/api/v1/ecos";

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void itemMasterCreateAndPatchProduceTwoAuditRows() {
        String token = engineerToken();

        // create
        ResponseEntity<Map> created = restTemplate.exchange(
                ITEM_BASE, HttpMethod.POST,
                jsonRequest(token, baseItemRequest("AUDIT-IM-001", "A")), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String itemId = extractId(created.getHeaders().getLocation().getPath());

        // patch
        ResponseEntity<Map> patched = restTemplate.exchange(
                ITEM_BASE + "/" + itemId, HttpMethod.PATCH,
                jsonRequest(token, Map.of("description", "Patched description")), Map.class);
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM work_order.item_master_aud WHERE id = ?::uuid",
                Integer.class, itemId);

        assertThat(count).as("item_master_aud should have 2 rows (INSERT + UPDATE)").isEqualTo(2);
    }

    @Test
    void bomReleaseProducesAuditRow() {
        String token = engineerToken();

        // create parent item and BOM
        String parentId = createItem(token, "AUDIT-BOM-001", "A");
        ResponseEntity<Map> bomResp = restTemplate.exchange(
                BOM_BASE, HttpMethod.POST,
                jsonRequest(token, Map.of("parentItemId", parentId, "bomRevision", "REV-A")), Map.class);
        assertThat(bomResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String bomId = bomResp.getBody().get("id").toString();

        // release
        ResponseEntity<Map> released = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/release", HttpMethod.POST,
                jsonRequest(token, Map.of()), Map.class);
        assertThat(released.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(released.getBody()).extractingByKey("status").isEqualTo("RELEASED");

        // at least 2 aud rows: one for create, one for release (status update)
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM work_order.bill_of_materials_aud WHERE id = ?::uuid",
                Integer.class, bomId);

        assertThat(count).as("bill_of_materials_aud should have at least 2 rows").isGreaterThanOrEqualTo(2);

        // the most recent row should have status RELEASED
        String latestStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM work_order.bill_of_materials_aud WHERE id = ?::uuid ORDER BY rev DESC LIMIT 1",
                String.class, bomId);

        assertThat(latestStatus).isEqualTo("RELEASED");
    }

    @Test
    void ecoApproveProducesAuditRow() {
        String token = sysAdminToken();
        String itemId = createItem(engineerToken(), "AUDIT-ECO-001", "A");

        // create ECO
        ResponseEntity<Map> ecoResp = restTemplate.exchange(
                ECO_BASE, HttpMethod.POST,
                jsonRequest(token, Map.of(
                        "title", "Audit ECO test",
                        "affectedItemIds", List.of(itemId))),
                Map.class);
        assertThat(ecoResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String ecoId = ecoResp.getBody().get("id").toString();

        // approve
        ResponseEntity<Map> approved = restTemplate.exchange(
                ECO_BASE + "/" + ecoId + "/approve", HttpMethod.POST,
                jsonRequest(token, Map.of()), Map.class);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);

        // at least 2 aud rows: create + approve
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM work_order.engineering_change_order_aud WHERE id = ?::uuid",
                Integer.class, ecoId);

        assertThat(count).as("engineering_change_order_aud should have at least 2 rows").isGreaterThanOrEqualTo(2);

        // most recent row should have status APPROVED
        String latestStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM work_order.engineering_change_order_aud"
                + " WHERE id = ?::uuid ORDER BY rev DESC LIMIT 1",
                String.class, ecoId);

        assertThat(latestStatus).isEqualTo("APPROVED");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String engineerToken() {
        return buildToken(ORG_ID, List.of("ENGINEER"));
    }

    private String sysAdminToken() {
        return buildToken(ORG_ID, List.of("SYSTEM_ADMIN"));
    }

    private String extractId(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private String createItem(String token, String partNumber, String revision) {
        ResponseEntity<Map> response = restTemplate.exchange(
                ITEM_BASE, HttpMethod.POST,
                jsonRequest(token, baseItemRequest(partNumber, revision)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return extractId(response.getHeaders().getLocation().getPath());
    }
}
