package com.mes.labour.integration.certification;

import com.mes.labour.integration.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CertificationControllerIT extends BaseIntegrationTest {

    private static final String ORG_ID = "11111111-1111-1111-1111-111111111111";
    private static final String BASE = "/api/v1/labour/certifications";

    private String adminToken() {
        return buildToken(ORG_ID, List.of("SYSTEM_ADMIN"));
    }

    @SuppressWarnings("unchecked")
    private String createEmployee(String number) {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/labour/employees", HttpMethod.POST,
                jsonRequest(adminToken(), Map.of(
                        "employeeNumber", number,
                        "firstName", "Cert",
                        "lastName", "Holder")),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").toString();
    }

    @SuppressWarnings("unchecked")
    private String createSkill(String code, Integer validityMonths) {
        Map<String, Object> body = validityMonths == null
                ? Map.of("skillCode", code, "name", "Skill " + code)
                : Map.of("skillCode", code, "name", "Skill " + code, "validityMonths", validityMonths);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/labour/skills", HttpMethod.POST,
                jsonRequest(adminToken(), body),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").toString();
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> award(String employeeId, String skillId, String awardDate) {
        return restTemplate.exchange(
                BASE, HttpMethod.POST,
                jsonRequest(adminToken(), Map.of(
                        "employeeId", employeeId,
                        "skillId", skillId,
                        "awardDate", awardDate)),
                Map.class);
    }

    @Test
    void awardDefaultsExpiryFromSkillValidity() {
        String emp = createEmployee("E-CERT-001");
        String skill = createSkill("SK-CERT-001", 24);

        ResponseEntity<Map> response = award(emp, skill, "2026-06-12");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("expiryDate")).isEqualTo("2028-06-12");
        assertThat(response.getBody().get("state")).isEqualTo("ACTIVE");
    }

    @Test
    void duplicateAwardSameDayReturns409() {
        String emp = createEmployee("E-CERT-002");
        String skill = createSkill("SK-CERT-002", 12);

        assertThat(award(emp, skill, "2026-06-12").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(award(emp, skill, "2026-06-12").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void awardAgainstInactiveSkillReturns422() {
        String emp = createEmployee("E-CERT-003");
        String skill = createSkill("SK-CERT-003", 12);
        restTemplate.exchange("/api/v1/labour/skills/" + skill, HttpMethod.PATCH,
                jsonRequest(adminToken(), Map.of("active", false)), Map.class);

        assertThat(award(emp, skill, "2026-06-12").getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void awardForInactiveEmployeeReturns422() {
        String emp = createEmployee("E-CERT-004");
        String skill = createSkill("SK-CERT-004", 12);
        restTemplate.exchange("/api/v1/labour/employees/" + emp, HttpMethod.PATCH,
                jsonRequest(adminToken(), Map.of("employmentStatus", "INACTIVE")), Map.class);

        assertThat(award(emp, skill, "2026-06-12").getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void pastExpiryReportsExpiredState() {
        String emp = createEmployee("E-CERT-005");
        String skill = createSkill("SK-CERT-005", 12);

        // awarded two years ago with 12-month validity → expired a year ago
        ResponseEntity<Map> created = award(emp, skill,
                LocalDate.now().minusYears(2).toString());
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> fetched = restTemplate.exchange(
                BASE + "/" + created.getBody().get("id"), HttpMethod.GET,
                bearerRequest(adminToken()), Map.class);
        assertThat(fetched.getBody().get("state")).isEqualTo("EXPIRED");
    }

    @Test
    void revokeRequiresReasonAndSetsRevokedState() {
        String emp = createEmployee("E-CERT-006");
        String skill = createSkill("SK-CERT-006", 12);
        String certId = award(emp, skill, "2026-06-12").getBody().get("id").toString();

        ResponseEntity<Map> noReason = restTemplate.exchange(
                BASE + "/" + certId + "/revoke", HttpMethod.POST,
                jsonRequest(adminToken(), Map.of()), Map.class);
        assertThat(noReason.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        ResponseEntity<Map> revoked = restTemplate.exchange(
                BASE + "/" + certId + "/revoke", HttpMethod.POST,
                jsonRequest(adminToken(), Map.of("reason", "Assessment invalidated")), Map.class);
        assertThat(revoked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(revoked.getBody().get("state")).isEqualTo("REVOKED");
        assertThat(revoked.getBody().get("revocationReason")).isEqualTo("Assessment invalidated");
        assertThat(revoked.getBody().get("revokedBy")).isNotNull();
    }

    @Test
    void expiringWithinDaysFilterFindsBoundaryCertifications() {
        String emp = createEmployee("E-CERT-007");
        String skillSoon = createSkill("SK-CERT-007A", null);
        String skillFar = createSkill("SK-CERT-007B", null);

        // explicit expiry dates: one inside a 60-day window, one outside
        restTemplate.exchange(BASE, HttpMethod.POST,
                jsonRequest(adminToken(), Map.of(
                        "employeeId", emp, "skillId", skillSoon,
                        "awardDate", "2026-01-01",
                        "expiryDate", LocalDate.now().plusDays(30).toString())),
                Map.class);
        restTemplate.exchange(BASE, HttpMethod.POST,
                jsonRequest(adminToken(), Map.of(
                        "employeeId", emp, "skillId", skillFar,
                        "awardDate", "2026-01-01",
                        "expiryDate", LocalDate.now().plusDays(200).toString())),
                Map.class);

        ResponseEntity<Map> window = restTemplate.exchange(
                BASE + "?employeeId=" + emp + "&expiringWithinDays=60", HttpMethod.GET,
                bearerRequest(adminToken()), Map.class);
        assertThat(window.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) window.getBody().get("content")).hasSize(1);
    }

    @Test
    void employeeProfileAggregatesCertificationsWithStates() {
        String emp = createEmployee("E-CERT-008");
        String skill = createSkill("SK-CERT-008", 24);
        award(emp, skill, "2026-06-12");

        ResponseEntity<Map> profile = restTemplate.exchange(
                "/api/v1/labour/employees/" + emp + "/profile", HttpMethod.GET,
                bearerRequest(adminToken()), Map.class);
        assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> certs =
                (List<Map<String, Object>>) profile.getBody().get("certifications");
        assertThat(certs).hasSize(1);
        assertThat(certs.get(0).get("state")).isEqualTo("ACTIVE");
        assertThat(certs.get(0).get("skillCode")).isEqualTo("SK-CERT-008");
    }
}
