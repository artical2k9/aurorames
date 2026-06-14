package com.mes.engineering.integration.workinstruction;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.mes.engineering.integration.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class QualificationEvaluationIT extends BaseIntegrationTest {

    private static final String ORG = "77777777-7777-7777-7777-777777777777";
    private static final String BASE = "/api/v1/work-instructions";
    private static final String EVALUATE = "/api/v1/labour/qualifications/evaluate";

    private static final WireMockServer LABOUR =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        LABOUR.start();
    }

    @DynamicPropertySource
    static void labourProps(DynamicPropertyRegistry registry) {
        registry.add("mes.engineering.labour-service-url", LABOUR::baseUrl);
    }

    @BeforeEach
    void resetStubs() {
        LABOUR.resetAll();
    }

    private String token() {
        return buildToken(ORG, List.of("ENGINEER"));
    }

    private void stubSkill(UUID skillId, String code) {
        LABOUR.stubFor(get(urlEqualTo("/api/v1/labour/skills/" + skillId))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"" + skillId + "\",\"skillCode\":\"" + code
                                + "\",\"name\":\"" + code + " name\"}")));
    }

    private void stubEvaluate(String resultsJson, boolean employeeActive) {
        LABOUR.stubFor(post(urlEqualTo(EVALUATE))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"employeeId\":\"" + UUID.randomUUID() + "\",\"employeeActive\":"
                                + employeeActive + ",\"results\":" + resultsJson + "}")));
    }

    @SuppressWarnings("unchecked")
    private UUID createWithTwoSkills(String identifier, UUID skill1, UUID skill2) {
        ResponseEntity<Map<String, Object>> create = restTemplate.exchange(
                BASE, HttpMethod.POST,
                jsonRequest(token(), Map.of("identifier", identifier, "title", "Qual WI")),
                new ParameterizedTypeReference<>() { });
        UUID id = UUID.fromString((String) create.getBody().get("id"));
        stubSkill(skill1, "SK1");
        stubSkill(skill2, "SK2");
        addSkill(id, skill1);
        addSkill(id, skill2);
        return id;
    }

    private void addSkill(UUID wiId, UUID skillId) {
        restTemplate.exchange(BASE + "/" + wiId + "/skill-requirements", HttpMethod.POST,
                jsonRequest(token(), Map.of("skillId", skillId.toString())),
                new ParameterizedTypeReference<Map<String, Object>>() { });
    }

    private ResponseEntity<Map<String, Object>> evaluate(UUID wiId, UUID employeeId) {
        return restTemplate.exchange(
                BASE + "/" + wiId + "/qualification?employeeId=" + employeeId, HttpMethod.GET,
                new HttpEntity<>(bearer(token())), new ParameterizedTypeReference<>() { });
    }

    private static String result(UUID skillId, String code, String status) {
        return "{\"skillId\":\"" + skillId + "\",\"skillCode\":\"" + code
                + "\",\"status\":\"" + status + "\"}";
    }

    @Test
    @SuppressWarnings("unchecked")
    void operatorMissingOneSkillIsNotQualified() {
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        UUID wi = createWithTwoSkills("WI-QUAL-MISSING", s1, s2);
        stubEvaluate("[" + result(s1, "SK1", "HELD_ACTIVE") + ","
                + result(s2, "SK2", "NOT_HELD") + "]", true);

        ResponseEntity<Map<String, Object>> resp = evaluate(wi, UUID.randomUUID());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("qualified")).isEqualTo(false);
        assertThat(resp.getBody().get("reason")).isEqualTo("OK");
        List<Map<String, Object>> missing = (List<Map<String, Object>>) resp.getBody().get("missing");
        assertThat(missing).hasSize(1);
        assertThat(missing.get(0).get("skillCode")).isEqualTo("SK2");
        assertThat(missing.get(0).get("state")).isEqualTo("NOT_HELD");
    }

    @Test
    void expiringSoonStillQualifies() {
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        UUID wi = createWithTwoSkills("WI-QUAL-EXPSOON", s1, s2);
        stubEvaluate("[" + result(s1, "SK1", "HELD_ACTIVE") + ","
                + result(s2, "SK2", "EXPIRING_SOON") + "]", true);

        ResponseEntity<Map<String, Object>> resp = evaluate(wi, UUID.randomUUID());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("qualified")).isEqualTo(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void expiredAndRevokedDoNotQualify() {
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        UUID wi = createWithTwoSkills("WI-QUAL-EXPIRED", s1, s2);
        stubEvaluate("[" + result(s1, "SK1", "EXPIRED") + ","
                + result(s2, "SK2", "REVOKED") + "]", true);

        ResponseEntity<Map<String, Object>> resp = evaluate(wi, UUID.randomUUID());

        assertThat(resp.getBody().get("qualified")).isEqualTo(false);
        List<Map<String, Object>> missing = (List<Map<String, Object>>) resp.getBody().get("missing");
        assertThat(missing).hasSize(2);
    }

    @Test
    void labourServiceUnavailableFailsClosed() {
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        UUID wi = createWithTwoSkills("WI-QUAL-DOWN", s1, s2);
        LABOUR.stubFor(post(urlEqualTo(EVALUATE)).willReturn(aResponse().withStatus(500)));

        ResponseEntity<Map<String, Object>> resp = evaluate(wi, UUID.randomUUID());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("qualified")).isEqualTo(false);
        assertThat(resp.getBody().get("reason")).isEqualTo("VERIFICATION_UNAVAILABLE");
    }

    @Test
    void noSkillRequirementsIsQualified() {
        ResponseEntity<Map<String, Object>> create = restTemplate.exchange(
                BASE, HttpMethod.POST,
                jsonRequest(token(), Map.of("identifier", "WI-QUAL-NONE", "title", "No skills")),
                new ParameterizedTypeReference<>() { });
        UUID wi = UUID.fromString((String) create.getBody().get("id"));

        ResponseEntity<Map<String, Object>> resp = evaluate(wi, UUID.randomUUID());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("qualified")).isEqualTo(true);
        assertThat(resp.getBody().get("reason")).isEqualTo("OK");
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
