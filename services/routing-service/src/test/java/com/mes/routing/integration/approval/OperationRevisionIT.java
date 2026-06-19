package com.mes.routing.integration.approval;

import com.mes.routing.integration.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** US8 — operation revision: content-only edit, operation-level approval, audit grouping/re-anchor. */
class OperationRevisionIT extends BaseIntegrationTest {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAP =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
            new ParameterizedTypeReference<>() { };

    private String admin() {
        return buildToken(DEV_ORG, List.of("SYSTEM_ADMIN"));
    }

    private String standardRouteTypeId() {
        return (String) restTemplate.exchange("/api/v1/routing/route-types", HttpMethod.GET,
                bearerRequest(admin()), LIST_OF_MAP).getBody().stream()
                .filter(m -> "STANDARD".equals(m.get("code"))).findFirst().orElseThrow().get("id");
    }

    private ResponseEntity<Map> post(String path, Map<String, Object> body) {
        return restTemplate.exchange(path, HttpMethod.POST, jsonRequest(admin(), body), Map.class);
    }

    private ResponseEntity<Map> patch(String path, Map<String, Object> body) {
        return restTemplate.exchange(path, HttpMethod.PATCH, jsonRequest(admin(), body), Map.class);
    }

    private String[] approvedRouteAndOperation() {
        String routeId = (String) post("/api/v1/routes", Map.of("partId", UUID.randomUUID().toString(),
                "partRevision", "A", "routeTypeId", standardRouteTypeId(),
                "reasonForRevision", "Initial")).getBody().get("id");
        String opId = (String) post("/api/v1/routes/" + routeId + "/operations",
                Map.of("operationNumber", 10, "sequenceNumber", 10)).getBody().get("id");
        post("/api/v1/routes/" + routeId + "/submit", Map.of());
        post("/api/v1/routes/" + routeId + "/approve", Map.of("password", "goodpass", "meaning", "Approved"));
        return new String[]{routeId, opId};
    }

    @Test
    void operationRevision_contentOnlyEdit_operationLevelApproval_andAuditGrouping() {
        String[] ro = approvedRouteAndOperation();
        String routeId = ro[0];
        String opId = ro[1];
        String opBase = "/api/v1/routes/" + routeId + "/operations/" + opId;

        // Start operation revision: operation reopens to DRAFT, revision bumps, governed by route rev 0.
        ResponseEntity<Map> started = post(opBase + "/revision", Map.of());
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(started.getBody().get("operationStatus")).isEqualTo("DRAFT");
        assertThat(started.getBody().get("operationRevision")).isEqualTo(1);

        // Content-only edit, then submit and approve at the operation level.
        ResponseEntity<Map> edited = patch(opBase + "/content", Map.of("description", "Updated method"));
        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(edited.getBody().get("description")).isEqualTo("Updated method");

        post(opBase + "/submit", Map.of());
        ResponseEntity<Map> approvedOp = post(opBase + "/approve",
                Map.of("password", "goodpass", "meaning", "Operation approved"));
        assertThat(approvedOp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(approvedOp.getBody().get("subjectType")).isEqualTo("OPERATION_REVISION");

        // Audit history groups the operation approval under the governing route revision (0).
        Map<String, Object> history = restTemplate.exchange(
                "/api/v1/routes/" + routeId + "/approval-history", HttpMethod.GET,
                bearerRequest(admin()), MAP).getBody();
        List<Map<String, Object>> revisions = (List<Map<String, Object>>) history.get("revisions");
        Map<String, Object> rev0 = revisions.stream()
                .filter(r -> Integer.valueOf(0).equals(r.get("routeRevision"))).findFirst().orElseThrow();
        assertThat((List<?>) rev0.get("routeApprovals")).hasSize(1);
        assertThat((List<?>) rev0.get("operationApprovals")).hasSize(1);
    }

    @Test
    void contentEdit_withoutOpenRevision_returns409() {
        String[] ro = approvedRouteAndOperation();
        ResponseEntity<Map> r = patch("/api/v1/routes/" + ro[0] + "/operations/" + ro[1] + "/content",
                Map.of("description", "No revision started"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void detailTabs_editableOnlyDuringOpenOperationRevision_andLabourTypeIsContent() {
        String[] ro = approvedRouteAndOperation();
        String opBase = "/api/v1/routes/" + ro[0] + "/operations/" + ro[1];

        // On an approved route with no open revision, a detail-tab edit (Skills) is blocked.
        ResponseEntity<Map> blocked = post(opBase + "/skills",
                Map.of("skillId", UUID.randomUUID().toString()));
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Open an operation revision → the same detail-tab edit now succeeds.
        post(opBase + "/revision", Map.of());
        ResponseEntity<Map> added = post(opBase + "/skills",
                Map.of("skillId", UUID.randomUUID().toString()));
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // labourType is a content-patch field on the operation revision.
        ResponseEntity<Map> content = patch(opBase + "/content", Map.of("labourType", "INDIRECT"));
        assertThat(content.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(content.getBody().get("labourType")).isEqualTo("INDIRECT");
    }
}
