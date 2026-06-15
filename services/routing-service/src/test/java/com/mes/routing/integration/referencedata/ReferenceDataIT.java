package com.mes.routing.integration.referencedata;

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

class ReferenceDataIT extends BaseIntegrationTest {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAP =
            new ParameterizedTypeReference<>() { };

    private String admin() {
        return buildToken(DEV_ORG, List.of("SYSTEM_ADMIN"));
    }

    private List<Map<String, Object>> list(String path, String token) {
        ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                path, HttpMethod.GET, bearerRequest(token), LIST_OF_MAP);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    @Test
    void seededDefaultsPresentForDevOrg() {
        List<String> routeTypeCodes = list("/api/v1/routing/route-types", admin())
                .stream().map(m -> (String) m.get("code")).toList();
        assertThat(routeTypeCodes).contains("STANDARD");

        List<String> planTypeCodes = list("/api/v1/routing/labour-plan-types", admin())
                .stream().map(m -> (String) m.get("code")).toList();
        assertThat(planTypeCodes).contains("MACHINE", "PEOPLE", "OSP");
    }

    @Test
    void workCentreCreateListAndDuplicateRejected() {
        var body = Map.<String, Object>of("code", "WC-100", "name", "Mill cell 1");
        ResponseEntity<Map> created = restTemplate.exchange(
                "/api/v1/routing/work-centres", HttpMethod.POST, jsonRequest(admin(), body), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(list("/api/v1/routing/work-centres", admin())
                .stream().map(m -> (String) m.get("code")).toList()).contains("WC-100");

        ResponseEntity<Map> dup = restTemplate.exchange(
                "/api/v1/routing/work-centres", HttpMethod.POST, jsonRequest(admin(), body), Map.class);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void workCentresAreOrgScoped() {
        restTemplate.exchange("/api/v1/routing/work-centres", HttpMethod.POST,
                jsonRequest(admin(), Map.of("code", "WC-ORG", "name", "Org-scoped cell")), Map.class);

        String otherOrg = buildToken(UUID.randomUUID().toString(), List.of("SYSTEM_ADMIN"));
        assertThat(list("/api/v1/routing/work-centres", otherOrg)
                .stream().map(m -> (String) m.get("code")).toList()).doesNotContain("WC-ORG");
    }

    @Test
    void seededRouteTypeCannotBeDeleted() {
        Map<String, Object> standard = list("/api/v1/routing/route-types", admin())
                .stream().filter(m -> "STANDARD".equals(m.get("code"))).findFirst().orElseThrow();
        String id = (String) standard.get("id");

        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/routing/route-types/" + id, HttpMethod.DELETE, bearerRequest(admin()), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void labourPlanTypeInUseByLabourCodeCannotBeDeleted() {
        ResponseEntity<Map> planType = restTemplate.exchange(
                "/api/v1/routing/labour-plan-types", HttpMethod.POST,
                jsonRequest(admin(), Map.of("code", "SUBCON", "name", "Subcontract")), Map.class);
        String planTypeId = (String) planType.getBody().get("id");

        restTemplate.exchange("/api/v1/routing/labour-codes", HttpMethod.POST,
                jsonRequest(admin(), Map.of("code", "LC-1", "name", "Welder", "labourPlanTypeId", planTypeId)),
                Map.class);

        ResponseEntity<Map> del = restTemplate.exchange(
                "/api/v1/routing/labour-plan-types/" + planTypeId, HttpMethod.DELETE,
                bearerRequest(admin()), Map.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void significantProcessTypeAndSupplierCanBeCreated() {
        ResponseEntity<Map> spt = restTemplate.exchange(
                "/api/v1/routing/significant-process-types", HttpMethod.POST,
                jsonRequest(admin(), Map.of("code", "BRAZE", "name", "Brazing",
                        "requiredApproverRole", "SME_BRAZING")), Map.class);
        assertThat(spt.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(spt.getBody().get("requiredApproverRole")).isEqualTo("SME_BRAZING");

        ResponseEntity<Map> sup = restTemplate.exchange(
                "/api/v1/routing/suppliers", HttpMethod.POST,
                jsonRequest(admin(), Map.of("code", "VENDOR-A", "name", "Heat Treat Co")), Map.class);
        assertThat(sup.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void writeRequiresManagePrivilege() {
        // ENGINEER has settings:view but not settings:manage → create rejected (403).
        String engineer = buildToken(DEV_ORG, List.of("ENGINEER"));
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/routing/work-centres", HttpMethod.POST,
                jsonRequest(engineer, Map.of("code", "WC-DENY", "name", "Denied")), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
