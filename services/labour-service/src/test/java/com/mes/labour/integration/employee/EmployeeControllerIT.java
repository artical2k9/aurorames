package com.mes.labour.integration.employee;

import com.mes.labour.integration.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EmployeeControllerIT extends BaseIntegrationTest {

    private static final String ORG_ID = "11111111-1111-1111-1111-111111111111";
    private static final String OTHER_ORG_ID = "22222222-2222-2222-2222-222222222222";
    private static final String BASE = "/api/v1/labour/employees";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminToken() {
        return buildToken(ORG_ID, List.of("SYSTEM_ADMIN"));
    }

    private String engineerToken() {
        return buildToken(ORG_ID, List.of("ENGINEER"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createEmployee(String token, String number) {
        ResponseEntity<Map> response = restTemplate.exchange(
                BASE, HttpMethod.POST,
                jsonRequest(token, Map.of(
                        "employeeNumber", number,
                        "firstName", "Ana",
                        "lastName", "Reyes",
                        "email", number.toLowerCase() + "@test.org")),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (Map<String, Object>) response.getBody();
    }

    @Test
    void createEmployeeReturns201AndWritesAuditRow() {
        Map<String, Object> body = createEmployee(adminToken(), "E-AUD-001");

        assertThat(body.get("id")).isNotNull();
        assertThat(body.get("employeeNumber")).isEqualTo("E-AUD-001");
        assertThat(body.get("employmentStatus")).isEqualTo("ACTIVE");

        Integer audRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM labour.employee_aud WHERE id = ?::uuid",
                Integer.class, body.get("id").toString());
        assertThat(audRows).isEqualTo(1);
    }

    @Test
    void duplicateEmployeeNumberReturns409() {
        createEmployee(adminToken(), "E-DUP-001");

        ResponseEntity<Map> response = restTemplate.exchange(
                BASE, HttpMethod.POST,
                jsonRequest(adminToken(), Map.of(
                        "employeeNumber", "E-DUP-001",
                        "firstName", "Dup",
                        "lastName", "User")),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void duplicateIamUserIdReturns409() {
        String iamUser = "iam-" + UUID.randomUUID();
        Map<String, Object> first = createEmployee(adminToken(), "E-IAM-001");
        restTemplate.exchange(BASE + "/" + first.get("id"), HttpMethod.PATCH,
                jsonRequest(adminToken(), Map.of("iamUserId", iamUser)), Map.class);

        Map<String, Object> second = createEmployee(adminToken(), "E-IAM-002");
        ResponseEntity<Map> response = restTemplate.exchange(
                BASE + "/" + second.get("id"), HttpMethod.PATCH,
                jsonRequest(adminToken(), Map.of("iamUserId", iamUser)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void listSupportsSearchAndStatusFilter() {
        createEmployee(adminToken(), "E-SRCH-UNIQ1");

        ResponseEntity<Map> search = restTemplate.exchange(
                BASE + "?search=E-SRCH-UNIQ1", HttpMethod.GET,
                bearerRequest(adminToken()), Map.class);
        assertThat(search.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> content = (List<?>) search.getBody().get("content");
        assertThat(content).hasSize(1);

        ResponseEntity<Map> filtered = restTemplate.exchange(
                BASE + "?status=INACTIVE&search=E-SRCH-UNIQ1", HttpMethod.GET,
                bearerRequest(adminToken()), Map.class);
        assertThat((List<?>) filtered.getBody().get("content")).isEmpty();
    }

    @Test
    void patchUpdatesFieldsAndDeactivates() {
        Map<String, Object> created = createEmployee(adminToken(), "E-PATCH-001");

        ResponseEntity<Map> patched = restTemplate.exchange(
                BASE + "/" + created.get("id"), HttpMethod.PATCH,
                jsonRequest(adminToken(), Map.of(
                        "email", "new.email@test.org",
                        "employmentStatus", "INACTIVE")),
                Map.class);
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patched.getBody().get("email")).isEqualTo("new.email@test.org");
        assertThat(patched.getBody().get("employmentStatus")).isEqualTo("INACTIVE");
    }

    @Test
    void iamLinkSetAndClearAndLookup() {
        String iamUser = "iam-" + UUID.randomUUID();
        Map<String, Object> created = createEmployee(adminToken(), "E-LINK-001");

        restTemplate.exchange(BASE + "/" + created.get("id"), HttpMethod.PATCH,
                jsonRequest(adminToken(), Map.of("iamUserId", iamUser)), Map.class);

        ResponseEntity<Map> byIam = restTemplate.exchange(
                BASE + "/by-iam-user/" + iamUser, HttpMethod.GET,
                bearerRequest(adminToken()), Map.class);
        assertThat(byIam.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(byIam.getBody().get("id")).isEqualTo(created.get("id"));

        ResponseEntity<Map> unknown = restTemplate.exchange(
                BASE + "/by-iam-user/no-such-user", HttpMethod.GET,
                bearerRequest(adminToken()), Map.class);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unauthenticatedReturns401() {
        ResponseEntity<Map> response = restTemplate.exchange(
                BASE, HttpMethod.GET, HttpEntity.EMPTY, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void engineerWithoutManagePrivilegeCannotCreate() {
        ResponseEntity<Map> response = restTemplate.exchange(
                BASE, HttpMethod.POST,
                jsonRequest(engineerToken(), Map.of(
                        "employeeNumber", "E-403-001",
                        "firstName", "No",
                        "lastName", "Access")),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void crossOrgIsolationReturns404() {
        Map<String, Object> created = createEmployee(adminToken(), "E-ORG-001");
        String otherOrgToken = buildToken(OTHER_ORG_ID, List.of("SYSTEM_ADMIN"));

        ResponseEntity<Map> response = restTemplate.exchange(
                BASE + "/" + created.get("id"), HttpMethod.GET,
                bearerRequest(otherOrgToken), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
