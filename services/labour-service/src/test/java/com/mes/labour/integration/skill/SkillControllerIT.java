package com.mes.labour.integration.skill;

import com.mes.labour.integration.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SkillControllerIT extends BaseIntegrationTest {

    private static final String ORG_ID = "11111111-1111-1111-1111-111111111111";
    private static final String OTHER_ORG_ID = "22222222-2222-2222-2222-222222222222";
    private static final String BASE = "/api/v1/labour/skills";

    private String adminToken() {
        return buildToken(ORG_ID, List.of("SYSTEM_ADMIN"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createSkill(String code, int validityMonths) {
        ResponseEntity<Map> response = restTemplate.exchange(
                BASE, HttpMethod.POST,
                jsonRequest(adminToken(), Map.of(
                        "skillCode", code,
                        "name", "Skill " + code,
                        "category", "welding",
                        "validityMonths", validityMonths)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (Map<String, Object>) response.getBody();
    }

    @Test
    void createSkillReturns201WithDefaults() {
        Map<String, Object> body = createSkill("SK-CREATE-001", 24);

        assertThat(body.get("id")).isNotNull();
        assertThat(body.get("skillCode")).isEqualTo("SK-CREATE-001");
        assertThat(body.get("active")).isEqualTo(true);
        assertThat(body.get("certificationRequired")).isEqualTo(true);
        assertThat(body.get("validityMonths")).isEqualTo(24);
    }

    @Test
    void duplicateSkillCodeReturns409() {
        createSkill("SK-DUP-001", 12);

        ResponseEntity<Map> response = restTemplate.exchange(
                BASE, HttpMethod.POST,
                jsonRequest(adminToken(), Map.of(
                        "skillCode", "SK-DUP-001",
                        "name", "Duplicate")),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void getReturnsStableConsumerContract() {
        Map<String, Object> created = createSkill("SK-CONTRACT-001", 6);

        ResponseEntity<Map> response = restTemplate.exchange(
                BASE + "/" + created.get("id"), HttpMethod.GET,
                bearerRequest(adminToken()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).containsKeys("id", "skillCode", "name", "active", "certificationRequired");
    }

    @Test
    void listSupportsBulkIdsAndActiveFilter() {
        Map<String, Object> s1 = createSkill("SK-BULK-001", 12);
        Map<String, Object> s2 = createSkill("SK-BULK-002", 12);

        ResponseEntity<Map> bulk = restTemplate.exchange(
                BASE + "?ids=" + s1.get("id") + "," + s2.get("id"), HttpMethod.GET,
                bearerRequest(adminToken()), Map.class);
        assertThat(bulk.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) bulk.getBody().get("content")).hasSize(2);

        // deactivate s1, filter active=true should exclude it
        restTemplate.exchange(BASE + "/" + s1.get("id"), HttpMethod.PATCH,
                jsonRequest(adminToken(), Map.of("active", false)), Map.class);

        ResponseEntity<Map> activeOnly = restTemplate.exchange(
                BASE + "?ids=" + s1.get("id") + "," + s2.get("id") + "&active=true", HttpMethod.GET,
                bearerRequest(adminToken()), Map.class);
        assertThat((List<?>) activeOnly.getBody().get("content")).hasSize(1);
    }

    @Test
    void patchDeactivatesSkill() {
        Map<String, Object> created = createSkill("SK-DEACT-001", 12);

        ResponseEntity<Map> patched = restTemplate.exchange(
                BASE + "/" + created.get("id"), HttpMethod.PATCH,
                jsonRequest(adminToken(), Map.of("active", false)), Map.class);
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patched.getBody().get("active")).isEqualTo(false);
    }

    @Test
    void crossOrgIsolationReturns404() {
        Map<String, Object> created = createSkill("SK-ORG-001", 12);
        String otherOrgToken = buildToken(OTHER_ORG_ID, List.of("SYSTEM_ADMIN"));

        ResponseEntity<Map> response = restTemplate.exchange(
                BASE + "/" + created.get("id"), HttpMethod.GET,
                bearerRequest(otherOrgToken), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
