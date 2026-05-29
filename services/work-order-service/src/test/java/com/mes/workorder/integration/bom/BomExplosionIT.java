package com.mes.workorder.integration.bom;

import com.mes.workorder.integration.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BomExplosionIT extends BaseIntegrationTest {

    static final String ORG_ID = "00000000-0000-0000-0000-000000000001";
    static final String BOM_BASE = "/api/v1/boms";
    static final String ITEM_BASE = "/api/v1/item-master";

    @Test
    void flatExplosionReturnsAllNodesWithCorrectDepths() {
        // 3-level tree: ASSY → SUBASSY (depth 1) → COMP1 (depth 2) → COMP2 (depth 3)
        String token = engineerToken();
        String assy = createItem(token, "EXPL-ASSY-001", "A");
        String subassy = createItem(token, "EXPL-SUB-001", "A");
        String comp1 = createItem(token, "EXPL-C1-001", "A");
        String comp2 = createItem(token, "EXPL-C2-001", "A");

        String assyBom = createBom(token, assy, "REV-A");
        String subassyBom = createBom(token, subassy, "REV-A");
        String comp1Bom = createBom(token, comp1, "REV-A");
        addLine(token, assyBom, subassy, "010");
        addLine(token, subassyBom, comp1, "010");
        addLine(token, comp1Bom, comp2, "010");

        ResponseEntity<List> response = restTemplate.exchange(
                BOM_BASE + "/" + assyBom + "/explosion?format=flat",
                HttpMethod.GET, bearerRequest(token), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(3);
        assertThat(response.getBody())
                .extracting(n -> ((Map<?, ?>) n).get("depth"))
                .containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    @SuppressWarnings("unchecked") // TestRestTemplate returns raw Map/List from JSON — cast safe by contract
    void indentedExplosionReturnsNestedTree() {
        // 2-level tree: PARENT → CHILD (depth 1) → GRANDCHILD (depth 2)
        String token = engineerToken();
        String parent = createItem(token, "INDT-PARENT-001", "A");
        String child = createItem(token, "INDT-CHILD-001", "A");
        String grandchild = createItem(token, "INDT-GC-001", "A");

        String parentBom = createBom(token, parent, "REV-A");
        String childBom = createBom(token, child, "REV-A");
        addLine(token, parentBom, child, "010");
        addLine(token, childBom, grandchild, "010");

        ResponseEntity<List> response = restTemplate.exchange(
                BOM_BASE + "/" + parentBom + "/explosion?format=indented",
                HttpMethod.GET, bearerRequest(token), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> topLevel = (List<Map<String, Object>>) response.getBody();
        assertThat(topLevel).hasSize(1);
        List<?> children = (List<?>) topLevel.get(0).get("children");
        assertThat(children).hasSize(1);
    }

    @Test
    void explosionExceedingMaxDepthReturns422() {
        // mes.bom.max-depth=3 (set in BaseIntegrationTest DynamicPropertySource).
        // A 4-level tree reaches depth 4 which exceeds max-depth and must return 422.
        String token = engineerToken();
        String l0 = createItem(token, "DEEP-L0-001", "A");
        String l1 = createItem(token, "DEEP-L1-001", "A");
        String l2 = createItem(token, "DEEP-L2-001", "A");
        String l3 = createItem(token, "DEEP-L3-001", "A");
        String l4 = createItem(token, "DEEP-L4-001", "A");

        String bom0 = createBom(token, l0, "REV-A");
        String bom1 = createBom(token, l1, "REV-A");
        String bom2 = createBom(token, l2, "REV-A");
        String bom3 = createBom(token, l3, "REV-A");
        addLine(token, bom0, l1, "010");
        addLine(token, bom1, l2, "010");
        addLine(token, bom2, l3, "010");
        addLine(token, bom3, l4, "010");

        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE + "/" + bom0 + "/explosion?format=flat",
                HttpMethod.GET, bearerRequest(token), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void circularLineAddReturns422() {
        // A's BOM has B as a component; adding A as a component of B's BOM creates a cycle.
        String token = engineerToken();
        String itemA = createItem(token, "CIRC-A-001", "A");
        String itemB = createItem(token, "CIRC-B-001", "A");

        String bomA = createBom(token, itemA, "REV-A");
        addLine(token, bomA, itemB, "010"); // A → B

        String bomB = createBom(token, itemB, "REV-A");
        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE + "/" + bomB + "/lines", HttpMethod.POST,
                jsonRequest(token, Map.of(
                        "componentItemId", itemA,
                        "quantity", 1.0,
                        "unitOfMeasure", "EA",
                        "findNumber", "010")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void counterfeitRiskAlertTrueForHighRiskComponent() {
        String token = engineerToken();
        String parentId = createItem(token, "RISK-PARENT-001", "A");
        String highRiskId = createHighRiskItem(token, "RISK-HIGH-001", "A");

        String bomId = createBom(token, parentId, "REV-A");
        addLine(token, bomId, highRiskId, "010");

        ResponseEntity<List> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/explosion?format=flat",
                HttpMethod.GET, bearerRequest(token), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody())
                .extracting(n -> ((Map<?, ?>) n).get("counterfeitRiskAlert"))
                .containsExactly(Boolean.TRUE);
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

    private String createHighRiskItem(String token, String partNumber, String revision) {
        Map<String, Object> req = new HashMap<>(baseItemRequest(partNumber, revision));
        req.put("counterfeitRiskLevel", "HIGH");
        ResponseEntity<Map> response = restTemplate.exchange(
                ITEM_BASE, HttpMethod.POST, jsonRequest(token, req), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String path = response.getHeaders().getLocation().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private String createBom(String token, String parentItemId, String bomRevision) {
        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE, HttpMethod.POST,
                jsonRequest(token, Map.of("parentItemId", parentItemId, "bomRevision", bomRevision)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").toString();
    }

    private void addLine(String token, String bomId, String componentItemId, String findNumber) {
        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines", HttpMethod.POST,
                jsonRequest(token, Map.of(
                        "componentItemId", componentItemId,
                        "quantity", 1.0,
                        "unitOfMeasure", "EA",
                        "findNumber", findNumber)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
