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
import static org.assertj.core.api.InstanceOfAssertFactories.list;

/** US4 — advanced operation types: derived Parallel, mutually-exclusive subsets, OSP resource rule. */
class OperationTypeIT extends BaseIntegrationTest {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAP =
            new ParameterizedTypeReference<>() { };

    private String admin() {
        return buildToken(DEV_ORG, List.of("SYSTEM_ADMIN"));
    }

    private String referenceId(String path, String code) {
        return (String) restTemplate.exchange("/api/v1/routing/" + path, HttpMethod.GET,
                bearerRequest(admin()), LIST_OF_MAP).getBody().stream()
                .filter(m -> code.equals(m.get("code"))).findFirst().orElseThrow().get("id");
    }

    private String createRoute() {
        return (String) restTemplate.exchange("/api/v1/routes", HttpMethod.POST,
                jsonRequest(admin(), Map.of("partId", UUID.randomUUID().toString(), "partRevision", "A",
                        "routeTypeId", referenceId("route-types", "STANDARD"), "reasonForRevision", "Initial")),
                Map.class).getBody().get("id");
    }

    private String addOperation(String routeId, int number, int sequence) {
        return (String) restTemplate.exchange("/api/v1/routes/" + routeId + "/operations", HttpMethod.POST,
                jsonRequest(admin(), Map.of("operationNumber", number, "sequenceNumber", sequence)),
                Map.class).getBody().get("id");
    }

    private List<Map<String, Object>> listOperations(String routeId) {
        return restTemplate.exchange("/api/v1/routes/" + routeId + "/operations", HttpMethod.GET,
                bearerRequest(admin()), LIST_OF_MAP).getBody();
    }

    @Test
    void sharedSequenceDerivesParallel() {
        String routeId = createRoute();
        addOperation(routeId, 20, 50);
        addOperation(routeId, 30, 50);

        assertThat(listOperations(routeId)).allSatisfy(op ->
                assertThat(op.get("derivedType")).isEqualTo("PARALLEL"));
    }

    @Test
    void uniqueSequenceDerivesNormal() {
        String routeId = createRoute();
        addOperation(routeId, 10, 10);
        assertThat(listOperations(routeId)).allSatisfy(op ->
                assertThat(op.get("derivedType")).isEqualTo("NORMAL"));
    }

    @Test
    void flaggingOspWithoutLabourResource_returns422() {
        String routeId = createRoute();
        String opId = addOperation(routeId, 10, 10);

        ResponseEntity<Map> r = restTemplate.exchange(
                "/api/v1/routes/" + routeId + "/operations/" + opId, HttpMethod.PATCH,
                jsonRequest(admin(), Map.of("osp", true)), Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void ospWithOspLabourResourceAndSupplier_persists() {
        String routeId = createRoute();
        String opId = addOperation(routeId, 10, 10);
        String supplierId = (String) restTemplate.exchange("/api/v1/routing/suppliers", HttpMethod.POST,
                jsonRequest(admin(), Map.of("code", "SUP-" + UUID.randomUUID(), "name", "Plating Ltd")),
                Map.class).getBody().get("id");

        ResponseEntity<Map> line = restTemplate.exchange(
                "/api/v1/routes/" + routeId + "/operations/" + opId + "/labour-plan", HttpMethod.POST,
                jsonRequest(admin(), Map.of("labourActivityType", "RUN", "basis", "PER_LOT",
                        "timeValue", 1, "labourPlanTypeId", referenceId("labour-plan-types", "OSP"))),
                Map.class);
        assertThat(line.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> patched = restTemplate.exchange(
                "/api/v1/routes/" + routeId + "/operations/" + opId, HttpMethod.PATCH,
                jsonRequest(admin(), Map.of("osp", true, "supplierId", supplierId)), Map.class);

        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patched.getBody().get("osp")).isEqualTo(true);
        assertThat(patched.getBody().get("supplierId")).isEqualTo(supplierId);
    }

    @Test
    void mutuallyExclusiveSubsetOfParallelSequence_persists() {
        String routeId = createRoute();
        addOperation(routeId, 20, 50);
        String op30 = addOperation(routeId, 30, 50);
        String op40 = addOperation(routeId, 40, 50);

        ResponseEntity<List<Map<String, Object>>> r = restTemplate.exchange(
                "/api/v1/routes/" + routeId + "/mutually-exclusive-sets", HttpMethod.PUT,
                jsonRequest(admin(), Map.of("sets", List.of(
                        Map.of("level", "OPERATION", "memberIds", List.of(op30, op40))))),
                LIST_OF_MAP);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).hasSize(1);
        assertThat(r.getBody().get(0).get("sequenceNumber")).isEqualTo(50);
        assertThat(r.getBody().get(0).get("memberIds")).asInstanceOf(list(String.class))
                .containsExactlyInAnyOrder(op30, op40);
    }

    @Test
    void mutuallyExclusiveOnNonParallelSequence_returns422() {
        String routeId = createRoute();
        String op10 = addOperation(routeId, 10, 10);
        String op20 = addOperation(routeId, 20, 20);

        ResponseEntity<Map> r = restTemplate.exchange(
                "/api/v1/routes/" + routeId + "/mutually-exclusive-sets", HttpMethod.PUT,
                jsonRequest(admin(), Map.of("sets", List.of(
                        Map.of("level", "OPERATION", "memberIds", List.of(op10, op20))))),
                Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
