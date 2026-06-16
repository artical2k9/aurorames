package com.mes.routing.integration.route;

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

/** US7/FR-031-033 — route edit lock: holder-only edits, unlock, privileged force-unlock. */
class RouteLockIT extends BaseIntegrationTest {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAP =
            new ParameterizedTypeReference<>() { };

    // Same SYSTEM_ADMIN role (so privileges pass) but distinct subjects → distinct lock identities.
    private String holder() {
        return buildToken(DEV_ORG, List.of("SYSTEM_ADMIN"), "holder-user");
    }

    private String other() {
        return buildToken(DEV_ORG, List.of("SYSTEM_ADMIN"), "other-user");
    }

    private String engineer() {
        return buildToken(DEV_ORG, List.of("ENGINEER"), "eng-user");
    }

    private String standardRouteTypeId(String token) {
        return (String) restTemplate.exchange("/api/v1/routing/route-types", HttpMethod.GET,
                bearerRequest(token), LIST_OF_MAP).getBody().stream()
                .filter(m -> "STANDARD".equals(m.get("code"))).findFirst().orElseThrow().get("id");
    }

    /** Creates a route as the holder (auto-locked to holder-user). */
    private String createRoute() {
        return (String) restTemplate.exchange("/api/v1/routes", HttpMethod.POST,
                jsonRequest(holder(), Map.of("partId", UUID.randomUUID().toString(), "partRevision", "A",
                        "routeTypeId", standardRouteTypeId(holder()), "reasonForRevision", "Initial")),
                Map.class).getBody().get("id");
    }

    private ResponseEntity<Map> addOp(String token, String routeId, int n) {
        return restTemplate.exchange("/api/v1/routes/" + routeId + "/operations", HttpMethod.POST,
                jsonRequest(token, Map.of("operationNumber", n, "sequenceNumber", n)), Map.class);
    }

    private ResponseEntity<Map> post(String token, String path) {
        return restTemplate.exchange(path, HttpMethod.POST, jsonRequest(token, Map.of()), Map.class);
    }

    @Test
    void createAutoLocksToCreator_holderEdits_othersBlocked() {
        String routeId = createRoute();
        // Created route is locked to the holder.
        Map<String, Object> route = restTemplate.exchange("/api/v1/routes/" + routeId, HttpMethod.GET,
                bearerRequest(holder()), Map.class).getBody();
        assertThat(route.get("lockHolder")).isEqualTo("holder-user");

        assertThat(addOp(holder(), routeId, 10).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // A different user (same privileges) cannot edit while the holder owns the lock.
        assertThat(addOp(other(), routeId, 20).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        // …nor steal the lock.
        assertThat(post(other(), "/api/v1/routes/" + routeId + "/lock").getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void unlockReleasesLock_thenAnotherUserCanAcquireAndEdit() {
        String routeId = createRoute();
        assertThat(post(holder(), "/api/v1/routes/" + routeId + "/unlock").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(post(other(), "/api/v1/routes/" + routeId + "/lock").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(addOp(other(), routeId, 10).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void privilegedUserCanForceUnlock_unprivilegedCannot() {
        String routeId = createRoute();
        // ENGINEER lacks routing:route:unlock → 403.
        assertThat(post(engineer(), "/api/v1/routes/" + routeId + "/force-unlock").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        // SYSTEM_ADMIN (other subject) holds routing:route:unlock → can break the holder's lock.
        assertThat(post(other(), "/api/v1/routes/" + routeId + "/force-unlock").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        // After force-unlock, the other user can take the lock and edit.
        assertThat(post(other(), "/api/v1/routes/" + routeId + "/lock").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(addOp(other(), routeId, 10).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
