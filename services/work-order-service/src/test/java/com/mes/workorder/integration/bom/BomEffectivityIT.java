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

import static org.assertj.core.api.Assertions.assertThat;

class BomEffectivityIT extends BaseIntegrationTest {

    static final String ORG_ID = "00000000-0000-0000-0000-000000000001";
    static final String BOM_BASE = "/api/v1/boms";
    static final String ITEM_BASE = "/api/v1/item-master";

    // AS1: date range inclusion/exclusion
    @Test
    @SuppressWarnings("unchecked")
    void dateEffectivityIncludesAndExcludesCorrectly() {
        String token = engineerToken();
        String parentId = createItem(token, "EFFD-PARENT-001", "A");
        String compId = createItem(token, "EFFD-COMP-001", "A");
        String bomId = createBom(token, parentId, "REV-A");

        // Add component with DATE effectivity covering 2025 only
        ResponseEntity<Map> addResp = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines", HttpMethod.POST,
                jsonRequest(token, Map.of(
                        "componentItemId", compId,
                        "quantity", 1.0,
                        "unitOfMeasure", "EA",
                        "findNumber", "010",
                        "effectivityMethod", "DATE",
                        "effectiveFromDate", "2025-01-01",
                        "effectiveToDate", "2025-12-31")),
                Map.class);
        assertThat(addResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Explosion at 2025-06-01 → component included
        ResponseEntity<List> included = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/explosion?format=flat&asOfDate=2025-06-01",
                HttpMethod.GET, bearerRequest(token), List.class);
        assertThat(included.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(included.getBody()).hasSize(1);

        // Explosion at 2026-01-01 → component's date window expired → 422 effectivity gap
        ResponseEntity<Map> excluded = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/explosion?format=flat&asOfDate=2026-01-01",
                HttpMethod.GET, bearerRequest(token), Map.class);
        assertThat(excluded.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    // AS2: unit range inclusion/exclusion
    @Test
    @SuppressWarnings("unchecked")
    void unitEffectivityIncludesAndExcludesCorrectly() {
        String token = engineerToken();
        String parentId = createItem(token, "EFFU-PARENT-001", "A");
        String compId = createItem(token, "EFFU-COMP-001", "A");
        String bomId = createBom(token, parentId, "REV-A");

        restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines", HttpMethod.POST,
                jsonRequest(token, Map.of(
                        "componentItemId", compId,
                        "quantity", 1.0,
                        "unitOfMeasure", "EA",
                        "findNumber", "010",
                        "effectivityMethod", "UNIT",
                        "effectiveFromUnit", "100",
                        "effectiveToUnit", "500")),
                Map.class);

        // Explosion at asOfUnit=200 → included
        ResponseEntity<List> included = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/explosion?format=flat&asOfUnit=200",
                HttpMethod.GET, bearerRequest(token), List.class);
        assertThat(included.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(included.getBody()).hasSize(1);

        // Explosion at asOfUnit=600 → beyond unit range → 422 gap
        ResponseEntity<Map> excluded = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/explosion?format=flat&asOfUnit=600",
                HttpMethod.GET, bearerRequest(token), Map.class);
        assertThat(excluded.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    // AS3: overlap → 422 message contains find number + conflicting line UUID
    @Test
    void overlappingDateLinesReturns422WithFindNumberAndConflictingId() {
        String token = engineerToken();
        String parentId = createItem(token, "EFFO-PARENT-001", "A");
        String compA = createItem(token, "EFFO-COMP-A-001", "A");
        String compB = createItem(token, "EFFO-COMP-B-001", "A");
        String bomId = createBom(token, parentId, "REV-A");

        // First line: findNumber 003, DATE 2025-01-01 to 2025-12-31
        ResponseEntity<Map> firstLine = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines", HttpMethod.POST,
                jsonRequest(token, Map.of(
                        "componentItemId", compA,
                        "quantity", 1.0,
                        "unitOfMeasure", "EA",
                        "findNumber", "003",
                        "effectivityMethod", "DATE",
                        "effectiveFromDate", "2025-01-01",
                        "effectiveToDate", "2025-12-31")),
                Map.class);
        assertThat(firstLine.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String conflictingLineId = firstLine.getBody().get("id").toString();

        // Second line: same findNumber 003, overlapping date range → 422
        ResponseEntity<Map> conflictResp = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines", HttpMethod.POST,
                jsonRequest(token, Map.of(
                        "componentItemId", compB,
                        "quantity", 1.0,
                        "unitOfMeasure", "EA",
                        "findNumber", "003",
                        "effectivityMethod", "DATE",
                        "effectiveFromDate", "2025-06-01",
                        "effectiveToDate", "2026-06-01")),
                Map.class);

        assertThat(conflictResp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        String message = conflictResp.getBody().get("message").toString();
        assertThat(message).contains("003");
        assertThat(message).contains(conflictingLineId);
    }

    // AS4: explosion for date with no covering line → 422 effectivity gap
    @Test
    void explosionWithNoCoveringLineSendsGapError() {
        String token = engineerToken();
        String parentId = createItem(token, "EFFG-PARENT-001", "A");
        String compId = createItem(token, "EFFG-COMP-001", "A");
        String bomId = createBom(token, parentId, "REV-A");

        // Line only covers 2025
        restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines", HttpMethod.POST,
                jsonRequest(token, Map.of(
                        "componentItemId", compId,
                        "quantity", 1.0,
                        "unitOfMeasure", "EA",
                        "findNumber", "010",
                        "effectivityMethod", "DATE",
                        "effectiveFromDate", "2025-01-01",
                        "effectiveToDate", "2025-12-31")),
                Map.class);

        // Query 2027 → gap error
        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/explosion?format=flat&asOfDate=2027-01-01",
                HttpMethod.GET, bearerRequest(token), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    // AS5: open-ended line (effectiveToDate null) included for all future dates
    @Test
    @SuppressWarnings("unchecked")
    void openEndedLineIncludedForFutureDates() {
        String token = engineerToken();
        String parentId = createItem(token, "EFOE-PARENT-001", "A");
        String compId = createItem(token, "EFOE-COMP-001", "A");
        String bomId = createBom(token, parentId, "REV-A");

        // Line with no effectiveToDate → open-ended
        restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines", HttpMethod.POST,
                jsonRequest(token, Map.of(
                        "componentItemId", compId,
                        "quantity", 1.0,
                        "unitOfMeasure", "EA",
                        "findNumber", "010",
                        "effectivityMethod", "DATE",
                        "effectiveFromDate", "2025-01-01")),
                Map.class);

        // Query a far-future date → still included
        ResponseEntity<List> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/explosion?format=flat&asOfDate=2099-12-31",
                HttpMethod.GET, bearerRequest(token), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
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
}
