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

/** US8 — route revision: an approved route reopens to DRAFT for structural edits and re-approval. */
class RouteRevisionIT extends BaseIntegrationTest {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAP =
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

    private String approvedRouteWithOperation() {
        String routeId = (String) post("/api/v1/routes", Map.of("partId", UUID.randomUUID().toString(),
                "partRevision", "A", "routeTypeId", standardRouteTypeId(),
                "reasonForRevision", "Initial")).getBody().get("id");
        post("/api/v1/routes/" + routeId + "/operations", Map.of("operationNumber", 10, "sequenceNumber", 10));
        post("/api/v1/routes/" + routeId + "/submit", Map.of());
        post("/api/v1/routes/" + routeId + "/approve", Map.of("password", "goodpass", "meaning", "Approved"));
        return routeId;
    }

    @Test
    void startRouteRevision_reopensDraftBumpsRevisionAllowsStructuralEdit() {
        String routeId = approvedRouteWithOperation();

        ResponseEntity<Map> rev = post("/api/v1/routes/" + routeId + "/revisions",
                Map.of("reasonForRevision", "Add finishing op"));
        assertThat(rev.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(rev.getBody().get("status")).isEqualTo("DRAFT");
        assertThat(rev.getBody().get("revision")).isEqualTo(1);

        // Structural edit (add operation) is now allowed again.
        ResponseEntity<Map> added = post("/api/v1/routes/" + routeId + "/operations",
                Map.of("operationNumber", 20, "sequenceNumber", 20));
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Re-approval of the new revision.
        post("/api/v1/routes/" + routeId + "/submit", Map.of());
        ResponseEntity<Map> approved = post("/api/v1/routes/" + routeId + "/approve",
                Map.of("password", "goodpass", "meaning", "Approved rev 1"));
        assertThat(approved.getBody().get("status")).isEqualTo("APPROVED");
        assertThat(approved.getBody().get("revision")).isEqualTo(1);
    }

    @Test
    void startRouteRevision_onDraftRoute_returns409() {
        String routeId = (String) post("/api/v1/routes", Map.of("partId", UUID.randomUUID().toString(),
                "partRevision", "A", "routeTypeId", standardRouteTypeId(),
                "reasonForRevision", "Initial")).getBody().get("id");

        ResponseEntity<Map> rev = post("/api/v1/routes/" + routeId + "/revisions", Map.of());
        assertThat(rev.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
