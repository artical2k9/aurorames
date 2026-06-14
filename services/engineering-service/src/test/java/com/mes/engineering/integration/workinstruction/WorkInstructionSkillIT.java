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
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class WorkInstructionSkillIT extends BaseIntegrationTest {

    private static final String ORG = "66666666-6666-6666-6666-666666666666";
    private static final String BASE = "/api/v1/work-instructions";

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

    private void stubSkill(UUID skillId, String code, String name) {
        LABOUR.stubFor(get(urlEqualTo("/api/v1/labour/skills/" + skillId))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"" + skillId + "\",\"skillCode\":\"" + code
                                + "\",\"name\":\"" + name + "\"}")));
    }

    @SuppressWarnings("unchecked")
    private UUID createInstructionWithStep(String identifier) {
        ResponseEntity<Map<String, Object>> create = restTemplate.exchange(
                BASE, HttpMethod.POST,
                jsonRequest(token(), Map.of("identifier", identifier, "title", "Skill WI")),
                new ParameterizedTypeReference<>() { });
        UUID id = UUID.fromString((String) create.getBody().get("id"));
        restTemplate.exchange(BASE + "/" + id + "/steps", HttpMethod.POST,
                jsonRequest(token(), Map.of("title", "Step", "bodyHtml", "<p>x</p>")),
                new ParameterizedTypeReference<Map<String, Object>>() { });
        return id;
    }

    private ResponseEntity<Map<String, Object>> addSkill(UUID wiId, UUID skillId) {
        return restTemplate.exchange(BASE + "/" + wiId + "/skill-requirements", HttpMethod.POST,
                jsonRequest(token(), Map.of("skillId", skillId.toString())),
                new ParameterizedTypeReference<>() { });
    }

    @Test
    void addSkillRequirementReturns201WithDenormalisedCodeAndName() {
        UUID wi = createInstructionWithStep("WI-SKILL-ADD");
        UUID skillId = UUID.randomUUID();
        stubSkill(skillId, "WELD-A", "TIG Welding A");

        ResponseEntity<Map<String, Object>> resp = addSkill(wi, skillId);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("skillCode")).isEqualTo("WELD-A");
        assertThat(resp.getBody().get("skillName")).isEqualTo("TIG Welding A");
        assertThat(resp.getBody().get("skillId")).isEqualTo(skillId.toString());
    }

    @Test
    void unknownSkillReturns422() {
        UUID wi = createInstructionWithStep("WI-SKILL-UNKNOWN");
        UUID skillId = UUID.randomUUID();
        LABOUR.stubFor(get(urlEqualTo("/api/v1/labour/skills/" + skillId))
                .willReturn(aResponse().withStatus(404)));

        ResponseEntity<Map<String, Object>> resp = addSkill(wi, skillId);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void duplicateSkillReturns409() {
        UUID wi = createInstructionWithStep("WI-SKILL-DUP");
        UUID skillId = UUID.randomUUID();
        stubSkill(skillId, "WELD-A", "TIG Welding A");

        assertThat(addSkill(wi, skillId).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(addSkill(wi, skillId).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listAndRemoveSkillRequirement() {
        UUID wi = createInstructionWithStep("WI-SKILL-LIST");
        UUID skillId = UUID.randomUUID();
        stubSkill(skillId, "INSP-1", "Visual Inspection");
        UUID reqId = UUID.fromString((String) addSkill(wi, skillId).getBody().get("id"));

        ResponseEntity<List<Map<String, Object>>> list = restTemplate.exchange(
                BASE + "/" + wi + "/skill-requirements", HttpMethod.GET,
                new HttpEntity<>(bearer(token())), new ParameterizedTypeReference<>() { });
        assertThat(list.getBody()).hasSize(1);
        assertThat(list.getBody().get(0).get("skillCode")).isEqualTo("INSP-1");

        ResponseEntity<Void> del = restTemplate.exchange(
                BASE + "/" + wi + "/skill-requirements/" + reqId, HttpMethod.DELETE,
                new HttpEntity<>(bearer(token())), Void.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<List<Map<String, Object>>> after = restTemplate.exchange(
                BASE + "/" + wi + "/skill-requirements", HttpMethod.GET,
                new HttpEntity<>(bearer(token())), new ParameterizedTypeReference<>() { });
        assertThat(after.getBody()).isEmpty();
    }

    @Test
    void addOnNonDraftRevisionReturns409() {
        UUID wi = createInstructionWithStep("WI-SKILL-PENDING");
        // Submit moves the only revision to PENDING_APPROVAL — no editable draft remains.
        ResponseEntity<Map<String, Object>> submit = restTemplate.exchange(
                BASE + "/" + wi + "/submit", HttpMethod.POST,
                new HttpEntity<>(bearer(token())), new ParameterizedTypeReference<>() { });
        assertThat(submit.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map<String, Object>> resp = addSkill(wi, UUID.randomUUID());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
