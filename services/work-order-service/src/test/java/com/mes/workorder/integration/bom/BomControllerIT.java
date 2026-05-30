package com.mes.workorder.integration.bom;

import com.mes.workorder.integration.BaseIntegrationTest;
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

class BomControllerIT extends BaseIntegrationTest {

    static final String ORG_ID = "00000000-0000-0000-0000-000000000001";
    static final String BOM_BASE = "/api/v1/boms";
    static final String ITEM_BASE = "/api/v1/item-master";

    @Test
    void createBomReturns201WithDraftStatus() {
        String token = engineerToken();
        String parentId = createItem(token, "BOM-HDR-001", "A");

        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE, HttpMethod.POST,
                jsonRequest(token, Map.of("parentItemId", parentId, "bomRevision", "REV-A")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).extractingByKey("status").isEqualTo("DRAFT");
        assertThat(response.getBody()).extractingByKey("id").isNotNull();
    }

    @Test
    void addBomLineReturns201() {
        String token = engineerToken();
        String parentId = createItem(token, "BOM-LN-PARENT-001", "A");
        String compId = createItem(token, "BOM-LN-COMP-001", "A");
        String bomId = createBom(token, parentId, "REV-A");

        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines", HttpMethod.POST,
                jsonRequest(token, Map.of(
                        "componentItemId", compId,
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
        String parentId = createItem(token, "BOM-LIST-PARENT-001", "A");
        String comp1Id = createItem(token, "BOM-LIST-COMP-001", "A");
        String comp2Id = createItem(token, "BOM-LIST-COMP-002", "A");
        String bomId = createBom(token, parentId, "REV-A");
        addLine(token, bomId, comp1Id, "010");
        addLine(token, bomId, comp2Id, "020");

        ResponseEntity<List> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines",
                HttpMethod.GET,
                bearerRequest(token),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    void releaseBomReturns200WithReleasedStatus() {
        String token = engineerToken();
        String parentId = createItem(token, "BOM-REL-PARENT-001", "A");
        String bomId = createBom(token, parentId, "REV-A");

        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/release", HttpMethod.POST,
                bearerRequest(token), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extractingByKey("status").isEqualTo("RELEASED");
    }

    @Test
    void addLineToReleasedBomReturns409() {
        String token = engineerToken();
        String parentId = createItem(token, "BOM-GUARD-PARENT-001", "A");
        String compId = createItem(token, "BOM-GUARD-COMP-001", "A");
        String bomId = createBom(token, parentId, "REV-A");
        releaseBom(token, bomId);

        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines", HttpMethod.POST,
                jsonRequest(token, Map.of(
                        "componentItemId", compId,
                        "quantity", 1.0,
                        "unitOfMeasure", "EA",
                        "findNumber", "010")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void patchBomLineQuantityReturns200WithUpdatedQuantity() {
        String token = engineerToken();
        String parentId = createItem(token, "BOM-PATCH-PARENT-001", "A");
        String compId = createItem(token, "BOM-PATCH-COMP-001", "A");
        String bomId = createBom(token, parentId, "REV-A");
        String lineId = addLineAndGetId(token, bomId, compId, "010");

        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines/" + lineId, HttpMethod.PATCH,
                jsonRequest(token, Map.of("quantity", 5.0)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("quantity").toString()).isEqualTo("5.0");
    }

    @Test
    void patchLineOnReleasedBomReturns409() {
        String token = engineerToken();
        String parentId = createItem(token, "BOM-PATCH-REL-PARENT-001", "A");
        String compId = createItem(token, "BOM-PATCH-REL-COMP-001", "A");
        String bomId = createBom(token, parentId, "REV-A");
        String lineId = addLineAndGetId(token, bomId, compId, "010");
        releaseBom(token, bomId);

        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines/" + lineId, HttpMethod.PATCH,
                jsonRequest(token, Map.of("quantity", 3.0)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void addLineWithNonExistentComponentReturns422() {
        String token = engineerToken();
        String parentId = createItem(token, "BOM-NF-PARENT-001", "A");
        String bomId = createBom(token, parentId, "REV-A");

        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines", HttpMethod.POST,
                jsonRequest(token, Map.of(
                        "componentItemId", UUID.randomUUID().toString(),
                        "quantity", 1.0,
                        "unitOfMeasure", "EA",
                        "findNumber", "010")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
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

    private String createBom(String token, String parentItemId, String bomRevision) {
        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE, HttpMethod.POST,
                jsonRequest(token, Map.of("parentItemId", parentItemId, "bomRevision", bomRevision)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").toString();
    }

    private String addLineAndGetId(String token, String bomId, String componentItemId, String findNumber) {
        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines", HttpMethod.POST,
                jsonRequest(token, Map.of(
                        "componentItemId", componentItemId,
                        "quantity", 1.0,
                        "unitOfMeasure", "EA",
                        "findNumber", findNumber)),
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

    private void releaseBom(String token, String bomId) {
        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/release", HttpMethod.POST,
                bearerRequest(token), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
