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

/** US5 — operation groups: grouping, derived Parallel group set, single-group membership, group-level ME. */
class OperationGroupIT extends BaseIntegrationTest {

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

    private String createRoute() {
        return (String) restTemplate.exchange("/api/v1/routes", HttpMethod.POST,
                jsonRequest(admin(), Map.of("partId", UUID.randomUUID().toString(), "partRevision", "A",
                        "routeTypeId", standardRouteTypeId(), "reasonForRevision", "Initial")),
                Map.class).getBody().get("id");
    }

    private String addOperation(String routeId, int number, int sequence) {
        return (String) restTemplate.exchange("/api/v1/routes/" + routeId + "/operations", HttpMethod.POST,
                jsonRequest(admin(), Map.of("operationNumber", number, "sequenceNumber", sequence)),
                Map.class).getBody().get("id");
    }

    private ResponseEntity<List<Map<String, Object>>> putGroups(String routeId, List<Map<String, Object>> groups) {
        return restTemplate.exchange("/api/v1/routes/" + routeId + "/groups", HttpMethod.PUT,
                jsonRequest(admin(), Map.of("groups", groups)), LIST_OF_MAP);
    }

    @Test
    void groupingAssignsOperationsAndDerivesNormal() {
        String routeId = createRoute();
        String op10 = addOperation(routeId, 10, 10);
        String op20 = addOperation(routeId, 20, 20);

        ResponseEntity<List<Map<String, Object>>> r = putGroups(routeId, List.of(
                Map.of("name", "Machining", "groupSequenceNumber", 10, "optional", false,
                        "operationIds", List.of(op10, op20))));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).hasSize(1);
        Map<String, Object> group = r.getBody().get(0);
        assertThat(group.get("derivedType")).isEqualTo("NORMAL");
        assertThat(group.get("operationIds")).asInstanceOf(list(String.class))
                .containsExactlyInAnyOrder(op10, op20);
    }

    @Test
    void sharedGroupSequenceDerivesParallelGroupSet() {
        String routeId = createRoute();
        String op10 = addOperation(routeId, 10, 10);
        String op20 = addOperation(routeId, 20, 20);

        ResponseEntity<List<Map<String, Object>>> r = putGroups(routeId, List.of(
                Map.of("name", "A", "groupSequenceNumber", 30, "optional", false, "operationIds", List.of(op10)),
                Map.of("name", "B", "groupSequenceNumber", 30, "optional", false, "operationIds", List.of(op20))));

        assertThat(r.getBody()).hasSize(2);
        assertThat(r.getBody()).allSatisfy(g -> assertThat(g.get("derivedType")).isEqualTo("PARALLEL"));
    }

    @Test
    void operationInTwoGroups_returns422() {
        String routeId = createRoute();
        String op10 = addOperation(routeId, 10, 10);

        ResponseEntity<Map> r = restTemplate.exchange("/api/v1/routes/" + routeId + "/groups",
                HttpMethod.PUT, jsonRequest(admin(), Map.of("groups", List.of(
                        Map.of("name", "A", "groupSequenceNumber", 10, "optional", false,
                                "operationIds", List.of(op10)),
                        Map.of("name", "B", "groupSequenceNumber", 20, "optional", false,
                                "operationIds", List.of(op10))))),
                Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void groupLevelMutuallyExclusiveSubset_persists() {
        String routeId = createRoute();
        String op10 = addOperation(routeId, 10, 10);
        String op20 = addOperation(routeId, 20, 20);
        String op30 = addOperation(routeId, 30, 30);

        List<Map<String, Object>> groups = putGroups(routeId, List.of(
                Map.of("name", "A", "groupSequenceNumber", 40, "optional", false, "operationIds", List.of(op10)),
                Map.of("name", "B", "groupSequenceNumber", 40, "optional", false, "operationIds", List.of(op20)),
                Map.of("name", "C", "groupSequenceNumber", 40, "optional", false, "operationIds", List.of(op30))))
                .getBody();
        String groupA = (String) groups.get(0).get("id");
        String groupB = (String) groups.get(1).get("id");

        ResponseEntity<List<Map<String, Object>>> me = restTemplate.exchange(
                "/api/v1/routes/" + routeId + "/mutually-exclusive-sets", HttpMethod.PUT,
                jsonRequest(admin(), Map.of("sets", List.of(
                        Map.of("level", "GROUP", "memberIds", List.of(groupA, groupB))))),
                LIST_OF_MAP);

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).hasSize(1);
        assertThat(me.getBody().get(0).get("level")).isEqualTo("GROUP");
        assertThat(me.getBody().get(0).get("sequenceNumber")).isEqualTo(40);
    }
}
