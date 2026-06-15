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

class OperationDetailIT extends BaseIntegrationTest {

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

    private String createWorkCentre() {
        return (String) restTemplate.exchange("/api/v1/routing/work-centres", HttpMethod.POST,
                jsonRequest(admin(), Map.of("code", "WC-" + UUID.randomUUID(), "name", "Cell")),
                Map.class).getBody().get("id");
    }

    private String[] createRouteAndOperation() {
        String routeId = (String) restTemplate.exchange("/api/v1/routes", HttpMethod.POST,
                jsonRequest(admin(), Map.of("partId", UUID.randomUUID().toString(), "partRevision", "A",
                        "routeTypeId", standardRouteTypeId(), "reasonForRevision", "Initial")),
                Map.class).getBody().get("id");
        String opId = (String) restTemplate.exchange("/api/v1/routes/" + routeId + "/operations",
                HttpMethod.POST, jsonRequest(admin(), Map.of("operationNumber", 10, "sequenceNumber", 10)),
                Map.class).getBody().get("id");
        return new String[]{routeId, opId};
    }

    private String base(String[] ro) {
        return "/api/v1/routes/" + ro[0] + "/operations/" + ro[1];
    }

    private void postOk(String path, Map<String, Object> body) {
        ResponseEntity<Map> r = restTemplate.exchange(path, HttpMethod.POST,
                jsonRequest(admin(), body), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private List<Map<String, Object>> list(String path) {
        return restTemplate.exchange(path, HttpMethod.GET, bearerRequest(admin()), LIST_OF_MAP).getBody();
    }

    @Test
    void fullOperationDetailProfilePersists() {
        String[] ro = createRouteAndOperation();
        String b = base(ro);

        postOk(b + "/resources", Map.of("workCentreId", createWorkCentre()));
        postOk(b + "/labour-plan", Map.of("labourActivityType", "SETUP", "basis", "PER_LOT", "timeValue", 1.5));
        postOk(b + "/labour-plan", Map.of("labourActivityType", "RUN", "basis", "PER_ITEM", "timeValue", 0.25));
        postOk(b + "/materials", Map.of("bomLineId", UUID.randomUUID().toString(), "mandatory", true));
        postOk(b + "/quality-variables", Map.of("inspectionCharacteristicId", UUID.randomUUID().toString()));
        postOk(b + "/tooling", Map.of("gageOrToolRef", "GAUGE-1", "description", "Bore gauge"));
        postOk(b + "/skills", Map.of("skillId", UUID.randomUUID().toString()));
        postOk(b + "/work-instruction", Map.of("workInstructionId", UUID.randomUUID().toString()));
        postOk(b + "/step-file", Map.of("reference", "PROG-1001.nc"));

        assertThat(list(b + "/resources")).hasSize(1);
        assertThat(list(b + "/labour-plan")).hasSize(2);
        assertThat(list(b + "/materials")).anySatisfy(m -> assertThat(m.get("mandatory")).isEqualTo(true));
        assertThat(list(b + "/tooling")).hasSize(1);
        assertThat(list(b + "/skills")).hasSize(1);
        assertThat(list(b + "/work-instruction")).hasSize(1);
        assertThat(list(b + "/step-file")).hasSize(1);
    }

    @Test
    void addResourceWithUnknownWorkCentre_returns404() {
        String[] ro = createRouteAndOperation();
        ResponseEntity<Map> r = restTemplate.exchange(base(ro) + "/resources", HttpMethod.POST,
                jsonRequest(admin(), Map.of("workCentreId", UUID.randomUUID().toString())), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteDetail_removesIt() {
        String[] ro = createRouteAndOperation();
        ResponseEntity<Map> created = restTemplate.exchange(base(ro) + "/skills", HttpMethod.POST,
                jsonRequest(admin(), Map.of("skillId", UUID.randomUUID().toString())), Map.class);
        String id = (String) created.getBody().get("id");

        ResponseEntity<Void> del = restTemplate.exchange(base(ro) + "/skills/" + id, HttpMethod.DELETE,
                bearerRequest(admin()), Void.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(list(base(ro) + "/skills")).isEmpty();
    }
}
