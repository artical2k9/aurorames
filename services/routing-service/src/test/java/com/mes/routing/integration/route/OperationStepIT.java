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

/** US6 — operation steps: CRUD, derived Parallel step set, step-level mutually-exclusive subset. */
class OperationStepIT extends BaseIntegrationTest {

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

    private String[] routeAndOperation() {
        String routeId = (String) restTemplate.exchange("/api/v1/routes", HttpMethod.POST,
                jsonRequest(admin(), Map.of("partId", UUID.randomUUID().toString(), "partRevision", "A",
                        "routeTypeId", standardRouteTypeId(), "reasonForRevision", "Initial")),
                Map.class).getBody().get("id");
        String opId = (String) restTemplate.exchange("/api/v1/routes/" + routeId + "/operations",
                HttpMethod.POST, jsonRequest(admin(), Map.of("operationNumber", 10, "sequenceNumber", 10)),
                Map.class).getBody().get("id");
        return new String[]{routeId, opId};
    }

    private String stepsBase(String[] ro) {
        return "/api/v1/routes/" + ro[0] + "/operations/" + ro[1] + "/steps";
    }

    private String addStep(String[] ro, int number, int sequence) {
        return (String) restTemplate.exchange(stepsBase(ro), HttpMethod.POST,
                jsonRequest(admin(), Map.of("stepNumber", number, "stepSequenceNumber", sequence,
                        "description", "Step " + number)), Map.class).getBody().get("id");
    }

    private List<Map<String, Object>> listSteps(String[] ro) {
        return restTemplate.exchange(stepsBase(ro), HttpMethod.GET, bearerRequest(admin()), LIST_OF_MAP)
                .getBody();
    }

    @Test
    void stepsPersistOrderedAndDeletable() {
        String[] ro = routeAndOperation();
        addStep(ro, 1, 10);
        addStep(ro, 2, 20);
        String step3 = addStep(ro, 3, 30);

        List<Map<String, Object>> steps = listSteps(ro);
        assertThat(steps).hasSize(3);
        assertThat(steps).allSatisfy(s -> assertThat(s.get("derivedType")).isEqualTo("NORMAL"));

        ResponseEntity<Void> del = restTemplate.exchange(stepsBase(ro) + "/" + step3, HttpMethod.DELETE,
                bearerRequest(admin()), Void.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(listSteps(ro)).hasSize(2);
    }

    @Test
    void duplicateStepNumber_returns409() {
        String[] ro = routeAndOperation();
        addStep(ro, 1, 10);
        ResponseEntity<Map> r = restTemplate.exchange(stepsBase(ro), HttpMethod.POST,
                jsonRequest(admin(), Map.of("stepNumber", 1, "stepSequenceNumber", 20)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void sharedStepSequenceDerivesParallel() {
        String[] ro = routeAndOperation();
        addStep(ro, 1, 10);
        addStep(ro, 2, 10);
        assertThat(listSteps(ro)).allSatisfy(s -> assertThat(s.get("derivedType")).isEqualTo("PARALLEL"));
    }

    @Test
    void stepLevelMutuallyExclusiveSubset_persists() {
        String[] ro = routeAndOperation();
        String s1 = addStep(ro, 1, 10);
        String s2 = addStep(ro, 2, 10);
        addStep(ro, 3, 10);

        ResponseEntity<List<Map<String, Object>>> me = restTemplate.exchange(
                "/api/v1/routes/" + ro[0] + "/mutually-exclusive-sets", HttpMethod.PUT,
                jsonRequest(admin(), Map.of("sets", List.of(
                        Map.of("level", "STEP", "memberIds", List.of(s1, s2))))),
                LIST_OF_MAP);

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).hasSize(1);
        assertThat(me.getBody().get(0).get("level")).isEqualTo("STEP");
        assertThat(me.getBody().get(0).get("memberIds")).asInstanceOf(list(String.class))
                .containsExactlyInAnyOrder(s1, s2);
    }
}
