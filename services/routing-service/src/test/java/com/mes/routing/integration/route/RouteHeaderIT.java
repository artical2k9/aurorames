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

class RouteHeaderIT extends BaseIntegrationTest {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAP =
            new ParameterizedTypeReference<>() { };

    private String admin() {
        return buildToken(DEV_ORG, List.of("SYSTEM_ADMIN"));
    }

    private String standardRouteTypeId() {
        ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                "/api/v1/routing/route-types", HttpMethod.GET, bearerRequest(admin()), LIST_OF_MAP);
        return (String) resp.getBody().stream()
                .filter(m -> "STANDARD".equals(m.get("code"))).findFirst().orElseThrow().get("id");
    }

    private String createAlternateType(String code) {
        ResponseEntity<Map> resp = restTemplate.exchange("/api/v1/routing/route-types", HttpMethod.POST,
                jsonRequest(admin(), Map.of("code", code, "name", code)), Map.class);
        return (String) resp.getBody().get("id");
    }

    @Test
    void createStandardRoute_returnsDraftRevision0() {
        Map<String, Object> body = Map.of("partId", UUID.randomUUID().toString(),
                "partRevision", "A", "routeTypeId", standardRouteTypeId(),
                "reasonForRevision", "Initial release");
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/routes", HttpMethod.POST, jsonRequest(admin(), body), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("status")).isEqualTo("DRAFT");
        assertThat(resp.getBody().get("revision")).isEqualTo(0);
    }

    @Test
    void secondStandardRouteForSamePartRevision_isRejected() {
        String part = UUID.randomUUID().toString();
        String std = standardRouteTypeId();
        Map<String, Object> body = Map.of("partId", part, "partRevision", "A",
                "routeTypeId", std, "reasonForRevision", "Initial");
        assertThat(restTemplate.exchange("/api/v1/routes", HttpMethod.POST,
                jsonRequest(admin(), body), Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> second = restTemplate.exchange("/api/v1/routes", HttpMethod.POST,
                jsonRequest(admin(), body), Map.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void alternateTypeRouteForSamePartRevision_isAccepted() {
        String part = UUID.randomUUID().toString();
        restTemplate.exchange("/api/v1/routes", HttpMethod.POST, jsonRequest(admin(),
                Map.of("partId", part, "partRevision", "A", "routeTypeId", standardRouteTypeId(),
                        "reasonForRevision", "Initial")), Map.class);

        ResponseEntity<Map> alt = restTemplate.exchange("/api/v1/routes", HttpMethod.POST,
                jsonRequest(admin(), Map.of("partId", part, "partRevision", "A",
                        "routeTypeId", createAlternateType("NPI"), "reasonForRevision", "NPI build")),
                Map.class);
        assertThat(alt.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void reasonForRevisionIsRequired() {
        Map<String, Object> body = Map.of("partId", UUID.randomUUID().toString(),
                "partRevision", "A", "routeTypeId", standardRouteTypeId());
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/routes", HttpMethod.POST, jsonRequest(admin(), body), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listWithAndWithoutSearchBothSucceed() {
        String reason = "Searchable-" + UUID.randomUUID();
        restTemplate.exchange("/api/v1/routes", HttpMethod.POST, jsonRequest(admin(),
                Map.of("partId", UUID.randomUUID().toString(), "partRevision", "A",
                        "routeTypeId", standardRouteTypeId(), "reasonForRevision", reason)), Map.class);

        // No-search path (the one that previously failed: null param typed as bytea).
        ResponseEntity<Map> noSearch = restTemplate.exchange("/api/v1/routes",
                HttpMethod.GET, bearerRequest(admin()), Map.class);
        assertThat(noSearch.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Search path matching the reason text.
        ResponseEntity<Map> searched = restTemplate.exchange(
                "/api/v1/routes?search=" + reason, HttpMethod.GET, bearerRequest(admin()), Map.class);
        assertThat(searched.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) searched.getBody().get("content");
        assertThat(content).anySatisfy(m -> assertThat(m.get("reasonForRevision")).isEqualTo(reason));
    }

    @Test
    void routesAreOrgScoped() {
        ResponseEntity<Map> created = restTemplate.exchange("/api/v1/routes", HttpMethod.POST,
                jsonRequest(admin(), Map.of("partId", UUID.randomUUID().toString(), "partRevision", "A",
                        "routeTypeId", standardRouteTypeId(), "reasonForRevision", "Initial")), Map.class);
        String id = (String) created.getBody().get("id");

        String otherOrg = buildToken(UUID.randomUUID().toString(), List.of("SYSTEM_ADMIN"));
        ResponseEntity<Map> list = restTemplate.exchange("/api/v1/routes", HttpMethod.GET,
                bearerRequest(otherOrg), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) list.getBody().get("content");
        assertThat(content.stream().map(m -> m.get("id"))).doesNotContain(id);
    }
}
