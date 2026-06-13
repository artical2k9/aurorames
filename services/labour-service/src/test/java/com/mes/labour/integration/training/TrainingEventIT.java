package com.mes.labour.integration.training;

import com.mes.labour.integration.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TrainingEventIT extends BaseIntegrationTest {

    private static final String ORG_ID = "11111111-1111-1111-1111-111111111111";
    private static final String BASE = "/api/v1/labour/training-events";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminToken() {
        return buildToken(ORG_ID, List.of("SYSTEM_ADMIN"));
    }

    @SuppressWarnings("unchecked")
    private String createEmployee(String number) {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/labour/employees", HttpMethod.POST,
                jsonRequest(adminToken(), Map.of(
                        "employeeNumber", number,
                        "firstName", "Train",
                        "lastName", "Ee")),
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

    @Test
    @SuppressWarnings("unchecked")
    void createEventWithTwoAttendeesAppearsInBothHistories() {
        String emp1 = createEmployee("E-TRN-001");
        String emp2 = createEmployee("E-TRN-002");
        String skill = createSkill("SK-TRN-001");

        ResponseEntity<Map> created = restTemplate.exchange(
                BASE, HttpMethod.POST,
                jsonRequest(adminToken(), Map.of(
                        "title", "IPC-A-610 refresher",
                        "trainingDate", "2026-06-01",
                        "durationMinutes", 240,
                        "trainer", "J. Smith",
                        "skillIds", List.of(skill),
                        "attendees", List.of(
                                Map.of("employeeId", emp1, "outcome", "COMPLETED"),
                                Map.of("employeeId", emp2, "outcome", "FAILED")))),
                Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        List<Map<String, Object>> attendees =
                (List<Map<String, Object>>) created.getBody().get("attendees");
        assertThat(attendees).hasSize(2);

        ResponseEntity<Map> history1 = restTemplate.exchange(
                "/api/v1/labour/employees/" + emp1 + "/training", HttpMethod.GET,
                bearerRequest(adminToken()), Map.class);
        assertThat(history1.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> records1 =
                (List<Map<String, Object>>) history1.getBody().get("content");
        assertThat(records1).hasSize(1);
        assertThat(records1.get(0).get("title")).isEqualTo("IPC-A-610 refresher");
        assertThat(records1.get(0).get("outcome")).isEqualTo("COMPLETED");

        ResponseEntity<Map> history2 = restTemplate.exchange(
                "/api/v1/labour/employees/" + emp2 + "/training", HttpMethod.GET,
                bearerRequest(adminToken()), Map.class);
        List<Map<String, Object>> records2 =
                (List<Map<String, Object>>) history2.getBody().get("content");
        assertThat(records2.get(0).get("outcome")).isEqualTo("FAILED");
    }

    @Test
    @SuppressWarnings("unchecked")
    void outcomeChangeIsAudited() {
        String emp = createEmployee("E-TRN-003");

        ResponseEntity<Map> created = restTemplate.exchange(
                BASE, HttpMethod.POST,
                jsonRequest(adminToken(), Map.of(
                        "title", "FOD awareness",
                        "trainingDate", "2026-06-02",
                        "attendees", List.of(Map.of("employeeId", emp, "outcome", "FAILED")))),
                Map.class);
        String eventId = created.getBody().get("id").toString();
        List<Map<String, Object>> attendees =
                (List<Map<String, Object>>) created.getBody().get("attendees");
        String attendanceId = attendees.get(0).get("id").toString();

        ResponseEntity<Map> patched = restTemplate.exchange(
                BASE + "/" + eventId, HttpMethod.PATCH,
                jsonRequest(adminToken(), Map.of(
                        "attendees", List.of(
                                Map.of("employeeId", emp, "outcome", "COMPLETED")))),
                Map.class);
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);

        Integer audRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM labour.training_attendance_aud WHERE id = ?::uuid",
                Integer.class, attendanceId);
        assertThat(audRows).isGreaterThanOrEqualTo(2);
    }

    @Test
    void unknownAttendeeReturns422() {
        ResponseEntity<Map> response = restTemplate.exchange(
                BASE, HttpMethod.POST,
                jsonRequest(adminToken(), Map.of(
                        "title", "Ghost training",
                        "trainingDate", "2026-06-03",
                        "attendees", List.of(Map.of(
                                "employeeId", "99999999-9999-9999-9999-999999999999",
                                "outcome", "COMPLETED")))),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void certificationDetailListsSupportingTraining() {
        String emp = createEmployee("E-TRN-004");
        String skill = createSkill("SK-TRN-004");

        restTemplate.exchange(BASE, HttpMethod.POST,
                jsonRequest(adminToken(), Map.of(
                        "title", "Welding cert prep",
                        "trainingDate", "2026-05-01",
                        "skillIds", List.of(skill),
                        "attendees", List.of(Map.of("employeeId", emp, "outcome", "COMPLETED")))),
                Map.class);

        ResponseEntity<Map> cert = restTemplate.exchange(
                "/api/v1/labour/certifications", HttpMethod.POST,
                jsonRequest(adminToken(), Map.of(
                        "employeeId", emp,
                        "skillId", skill,
                        "awardDate", "2026-06-01")),
                Map.class);
        assertThat(cert.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> detail = restTemplate.exchange(
                "/api/v1/labour/certifications/" + cert.getBody().get("id"), HttpMethod.GET,
                bearerRequest(adminToken()), Map.class);
        List<Map<String, Object>> supporting =
                (List<Map<String, Object>>) detail.getBody().get("supportingTraining");
        assertThat(supporting).hasSize(1);
        assertThat(supporting.get(0).get("title")).isEqualTo("Welding cert prep");
    }
}
