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

class RouteOperationIT extends BaseIntegrationTest {

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

    private String createRoute() {
        ResponseEntity<Map> resp = restTemplate.exchange("/api/v1/routes", HttpMethod.POST,
                jsonRequest(admin(), Map.of("partId", UUID.randomUUID().toString(), "partRevision", "A",
                        "routeTypeId", standardRouteTypeId(), "reasonForRevision", "Initial")), Map.class);
        return (String) resp.getBody().get("id");
    }

    private ResponseEntity<Map> addOp(String routeId, int opNo, int seq) {
        return restTemplate.exchange("/api/v1/routes/" + routeId + "/operations", HttpMethod.POST,
                jsonRequest(admin(), Map.of("operationNumber", opNo, "sequenceNumber", seq,
                        "description", "Op " + opNo)), Map.class);
    }

    private List<Map<String, Object>> listOps(String routeId) {
        return restTemplate.exchange("/api/v1/routes/" + routeId + "/operations", HttpMethod.GET,
                bearerRequest(admin()), LIST_OF_MAP).getBody();
    }

    @Test
    void addNormalOperationsAndListInOrder() {
        String route = createRoute();
        assertThat(addOp(route, 10, 10).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(addOp(route, 30, 30).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(addOp(route, 20, 20).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        List<Object> numbers = listOps(route).stream().map(m -> m.get("operationNumber")).toList();
        assertThat(numbers).containsExactly(10, 20, 30);
        assertThat(listOps(route)).allSatisfy(m -> assertThat(m.get("derivedType")).isEqualTo("NORMAL"));
    }

    @Test
    void duplicateOperationNumberRejected() {
        String route = createRoute();
        assertThat(addOp(route, 10, 10).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(addOp(route, 10, 20).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void sharedSequenceNumberDerivesParallel() {
        String route = createRoute();
        addOp(route, 30, 50);
        addOp(route, 40, 50);

        List<String> types = listOps(route).stream()
                .map(m -> (String) m.get("derivedType")).toList();
        assertThat(types).containsExactly("PARALLEL", "PARALLEL");
    }

    @Test
    void labourTypeDefaultsDirectAndCanBePatchedIndirect() {
        String route = createRoute();
        Map created = addOp(route, 10, 10).getBody();
        String opId = (String) created.get("id");
        assertThat(created.get("labourType")).isEqualTo("DIRECT");

        ResponseEntity<Map> patched = restTemplate.exchange(
                "/api/v1/routes/" + route + "/operations/" + opId, HttpMethod.PATCH,
                jsonRequest(admin(), Map.of("labourType", "INDIRECT")), Map.class);

        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patched.getBody().get("labourType")).isEqualTo("INDIRECT");
    }

    @Test
    void deleteOperationRemovesIt() {
        String route = createRoute();
        String opId = (String) addOp(route, 10, 10).getBody().get("id");
        addOp(route, 20, 20);

        ResponseEntity<Void> del = restTemplate.exchange(
                "/api/v1/routes/" + route + "/operations/" + opId, HttpMethod.DELETE,
                bearerRequest(admin()), Void.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(listOps(route).stream().map(m -> m.get("operationNumber"))).containsExactly(20);
    }
}
