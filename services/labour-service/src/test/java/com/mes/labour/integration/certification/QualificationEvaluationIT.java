package com.mes.labour.integration.certification;

import com.mes.labour.integration.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QualificationEvaluationIT extends BaseIntegrationTest {

    private static final String ORG_ID = "11111111-1111-1111-1111-111111111111";
    private static final String EVALUATE = "/api/v1/labour/qualifications/evaluate";

    private String adminToken() {
        return buildToken(ORG_ID, List.of("SYSTEM_ADMIN"));
    }

    @SuppressWarnings("unchecked")
    private String createEmployee(String number) {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/labour/employees", HttpMethod.POST,
                jsonRequest(adminToken(), Map.of(
                        "employeeNumber", number,
                        "firstName", "Qual",
                        "lastName", "Operator")),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").toString();
    }

    @SuppressWarnings("unchecked")
    private String createSkill(String code) {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/labour/skills", HttpMethod.POST,
                jsonRequest(adminToken(), Map.of("skillCode", code, "name", "Skill " + code)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").toString();
    }

    private void awardWithExpiry(String employeeId, String skillId, LocalDate expiry, boolean revoke) {
        @SuppressWarnings("unchecked")
        ResponseEntity<Map> created = restTemplate.exchange(
                "/api/v1/labour/certifications", HttpMethod.POST,
                jsonRequest(adminToken(), Map.of(
                        "employeeId", employeeId,
                        "skillId", skillId,
                        "awardDate", "2026-01-01",
                        "expiryDate", expiry.toString())),
                Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        if (revoke) {
            restTemplate.exchange(
                    "/api/v1/labour/certifications/" + created.getBody().get("id") + "/revoke",
                    HttpMethod.POST,
                    jsonRequest(adminToken(), Map.of("reason", "test revocation")), Map.class);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> evaluate(Map<String, Object> request) {
        ResponseEntity<Map> response = restTemplate.exchange(
                EVALUATE, HttpMethod.POST,
                jsonRequest(adminToken(), request), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) response.getBody();
    }

    @SuppressWarnings("unchecked")
    private String statusOf(Map<String, Object> evaluation, String skillId) {
        List<Map<String, Object>> results =
                (List<Map<String, Object>>) evaluation.get("results");
        return results.stream()
                .filter(r -> skillId.equals(r.get("skillId")))
                .findFirst()
                .map(r -> (String) r.get("status"))
                .orElseThrow();
    }

    @Test
    void evaluateCoversAllStatuses() {
        String emp = createEmployee("E-QUAL-001");
        String heldActive = createSkill("SK-QUAL-A");
        String expiringSoon = createSkill("SK-QUAL-B");
        String expired = createSkill("SK-QUAL-C");
        String revoked = createSkill("SK-QUAL-D");
        String notHeld = createSkill("SK-QUAL-E");
        String inactiveSkill = createSkill("SK-QUAL-F");

        awardWithExpiry(emp, heldActive, LocalDate.now().plusYears(1), false);
        awardWithExpiry(emp, expiringSoon, LocalDate.now().plusDays(10), false);
        awardWithExpiry(emp, expired, LocalDate.now().minusDays(1), false);
        awardWithExpiry(emp, revoked, LocalDate.now().plusYears(1), true);
        awardWithExpiry(emp, inactiveSkill, LocalDate.now().plusYears(1), false);
        restTemplate.exchange("/api/v1/labour/skills/" + inactiveSkill, HttpMethod.PATCH,
                jsonRequest(adminToken(), Map.of("active", false)), Map.class);

        Map<String, Object> evaluation = evaluate(Map.of(
                "employeeId", emp,
                "skillIds", List.of(heldActive, expiringSoon, expired, revoked, notHeld, inactiveSkill)));

        assertThat(evaluation.get("employeeActive")).isEqualTo(true);
        assertThat(statusOf(evaluation, heldActive)).isEqualTo("HELD_ACTIVE");
        assertThat(statusOf(evaluation, expiringSoon)).isEqualTo("EXPIRING_SOON");
        assertThat(statusOf(evaluation, expired)).isEqualTo("EXPIRED");
        assertThat(statusOf(evaluation, revoked)).isEqualTo("REVOKED");
        assertThat(statusOf(evaluation, notHeld)).isEqualTo("NOT_HELD");
        assertThat(statusOf(evaluation, inactiveSkill)).isEqualTo("SKILL_INACTIVE");
    }

    @Test
    void inactiveEmployeeReportsEmployeeActiveFalse() {
        String emp = createEmployee("E-QUAL-002");
        String skill = createSkill("SK-QUAL-G");
        awardWithExpiry(emp, skill, LocalDate.now().plusYears(1), false);
        restTemplate.exchange("/api/v1/labour/employees/" + emp, HttpMethod.PATCH,
                jsonRequest(adminToken(), Map.of("employmentStatus", "INACTIVE")), Map.class);

        Map<String, Object> evaluation = evaluate(Map.of(
                "employeeId", emp, "skillIds", List.of(skill)));

        assertThat(evaluation.get("employeeActive")).isEqualTo(false);
    }

    @Test
    void evaluateByIamUserId() {
        String iamUser = "iam-" + UUID.randomUUID();
        String emp = createEmployee("E-QUAL-003");
        restTemplate.exchange("/api/v1/labour/employees/" + emp, HttpMethod.PATCH,
                jsonRequest(adminToken(), Map.of("iamUserId", iamUser)), Map.class);
        String skill = createSkill("SK-QUAL-H");
        awardWithExpiry(emp, skill, LocalDate.now().plusYears(1), false);

        Map<String, Object> evaluation = evaluate(Map.of(
                "iamUserId", iamUser, "skillIds", List.of(skill)));

        assertThat(evaluation.get("employeeId")).isEqualTo(emp);
        assertThat(statusOf(evaluation, skill)).isEqualTo("HELD_ACTIVE");
    }

    @Test
    void emptySkillListReturnsEmptyResults() {
        String emp = createEmployee("E-QUAL-004");

        Map<String, Object> evaluation = evaluate(Map.of(
                "employeeId", emp, "skillIds", List.of()));

        assertThat((List<?>) evaluation.get("results")).isEmpty();
    }

    @Test
    void exactlyOneOfEmployeeIdOrIamUserIdRequired() {
        ResponseEntity<Map> neither = restTemplate.exchange(
                EVALUATE, HttpMethod.POST,
                jsonRequest(adminToken(), Map.of("skillIds", List.of())), Map.class);
        assertThat(neither.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        String emp = createEmployee("E-QUAL-005");
        ResponseEntity<Map> both = restTemplate.exchange(
                EVALUATE, HttpMethod.POST,
                jsonRequest(adminToken(), Map.of(
                        "employeeId", emp,
                        "iamUserId", "some-user",
                        "skillIds", List.of())),
                Map.class);
        assertThat(both.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownEmployeeReturns404() {
        ResponseEntity<Map> response = restTemplate.exchange(
                EVALUATE, HttpMethod.POST,
                jsonRequest(adminToken(), Map.of(
                        "employeeId", UUID.randomUUID().toString(),
                        "skillIds", List.of())),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void twentySkillEvaluateCompletesUnder500ms() {
        // SC-005: bulk qualification evaluate must stay under 500 ms for a 20-skill request.
        String emp = createEmployee("E-QUAL-PERF");
        java.util.List<String> skillIds = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String skillId = createSkill("SK-PERF-" + i);
            skillIds.add(skillId);
            awardWithExpiry(emp, skillId, LocalDate.now().plusYears(1), false);
        }
        Map<String, Object> request = Map.of("employeeId", emp, "skillIds", skillIds);

        // Warm up once so JIT / connection-pool priming does not skew the measured call.
        evaluate(request);

        long startNanos = System.nanoTime();
        Map<String, Object> evaluation = evaluate(request);
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;

        assertThat((List<?>) evaluation.get("results")).hasSize(20);
        assertThat(elapsedMillis)
                .as("20-skill evaluate latency (ms)")
                .isLessThan(500L);
    }

    @Test
    void renewalNewestCertificationGoverns() {
        String emp = createEmployee("E-QUAL-006");
        String skill = createSkill("SK-QUAL-I");
        // expired original
        awardWithExpiry(emp, skill, LocalDate.now().minusDays(10), false);
        // renewal with future expiry (different award date)
        restTemplate.exchange("/api/v1/labour/certifications", HttpMethod.POST,
                jsonRequest(adminToken(), Map.of(
                        "employeeId", emp, "skillId", skill,
                        "awardDate", LocalDate.now().toString(),
                        "expiryDate", LocalDate.now().plusYears(1).toString())),
                Map.class);

        Map<String, Object> evaluation = evaluate(Map.of(
                "employeeId", emp, "skillIds", List.of(skill)));

        assertThat(statusOf(evaluation, skill)).isEqualTo("HELD_ACTIVE");
    }
}
