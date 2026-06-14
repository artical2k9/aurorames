package com.mes.engineering.integration.workinstruction;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.mes.engineering.integration.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class WorkInstructionWorkflowIT extends BaseIntegrationTest {

    private static final String ORG = "44444444-4444-4444-4444-444444444444";
    private static final String BASE = "/api/v1/work-instructions";
    private static final String GOOD_PASSWORD = "correct-pass";

    private static final WireMockServer KC_WIREMOCK =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @BeforeAll
    static void startWireMock() {
        KC_WIREMOCK.start();
    }

    @AfterAll
    static void stopWireMock() {
        KC_WIREMOCK.stop();
    }

    @DynamicPropertySource
    static void signatureProps(DynamicPropertyRegistry registry) {
        registry.add("mes.signature.keycloak.token-uri", () -> KC_WIREMOCK.baseUrl() + "/token");
        registry.add("mes.signature.keycloak.client-id", () -> "mes-signature-verify");
        registry.add("mes.signature.keycloak.client-secret", () -> "test-secret");
    }

    @BeforeEach
    void stubKeycloak() {
        KC_WIREMOCK.resetAll();
        // Correct password -> KC 200 (token body is irrelevant; it is discarded).
        KC_WIREMOCK.stubFor(post(urlEqualTo("/token"))
                .withRequestBody(containing("password=" + GOOD_PASSWORD))
                .willReturn(okJson("{\"access_token\":\"ignored\",\"token_type\":\"Bearer\"}")));
        // Anything else -> KC 401 (invalid credentials).
        KC_WIREMOCK.stubFor(post(urlEqualTo("/token"))
                .atPriority(10)
                .willReturn(aResponse().withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"invalid_grant\"}")));
    }

    private String token() {
        return buildToken(ORG, List.of("ENGINEER"));
    }

    @SuppressWarnings("unchecked")
    private UUID createWithStepAndSubmit(String identifier) {
        ResponseEntity<Map<String, Object>> create = restTemplate.exchange(
                BASE, HttpMethod.POST,
                jsonRequest(token(), Map.of("identifier", identifier, "title", "Workflow")),
                new ParameterizedTypeReference<>() { });
        UUID id = UUID.fromString((String) create.getBody().get("id"));
        restTemplate.exchange(BASE + "/" + id + "/steps", HttpMethod.POST,
                jsonRequest(token(), Map.of("title", "Step", "bodyHtml", "<p>do</p>")),
                new ParameterizedTypeReference<Map<String, Object>>() { });
        restTemplate.exchange(BASE + "/" + id + "/submit", HttpMethod.POST,
                new HttpEntity<>(headers()), new ParameterizedTypeReference<Map<String, Object>>() { });
        return id;
    }

    @Test
    @SuppressWarnings("unchecked")
    void approveWithValidPasswordRecordsSignature() {
        UUID id = createWithStepAndSubmit("WI-WF-APPROVE");
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                BASE + "/" + id + "/approve", HttpMethod.POST,
                jsonRequest(token(), Map.of("password", GOOD_PASSWORD)),
                new ParameterizedTypeReference<>() { });
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("revisionStatus")).isEqualTo("APPROVED");
        assertThat(resp.getBody().get("approvedBy")).isNotNull();
        List<Map<String, Object>> signatures = (List<Map<String, Object>>) resp.getBody().get("signatures");
        assertThat(signatures).hasSize(1);
        assertThat(signatures.get(0).get("meaning")).isEqualTo("APPROVED");
        assertThat(signatures.get(0).get("signerFullName")).isEqualTo("Test Approver");
    }

    @Test
    void approveWithWrongPasswordReturns422AndKeepsPending() {
        UUID id = createWithStepAndSubmit("WI-WF-BADPASS");
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                BASE + "/" + id + "/approve", HttpMethod.POST,
                jsonRequest(token(), Map.of("password", "wrong-pass")),
                new ParameterizedTypeReference<>() { });
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(String.valueOf(resp.getBody().get("details"))).contains("SIGNATURE_VERIFICATION_FAILED");

        ResponseEntity<Map<String, Object>> get = restTemplate.exchange(
                BASE + "/" + id, HttpMethod.GET, new HttpEntity<>(headers()),
                new ParameterizedTypeReference<>() { });
        assertThat(get.getBody().get("revisionStatus")).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void rejectWithReasonReturnsToDraftAndWithoutReason422() {
        UUID id = createWithStepAndSubmit("WI-WF-REJECT");
        ResponseEntity<String> noReason = restTemplate.exchange(
                BASE + "/" + id + "/reject", HttpMethod.POST,
                jsonRequest(token(), Map.of("reason", "")), String.class);
        assertThat(noReason.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        ResponseEntity<Map<String, Object>> rejected = restTemplate.exchange(
                BASE + "/" + id + "/reject", HttpMethod.POST,
                jsonRequest(token(), Map.of("reason", "Missing torque spec")),
                new ParameterizedTypeReference<>() { });
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rejected.getBody().get("revisionStatus")).isEqualTo("DRAFT");
        assertThat(rejected.getBody().get("rejectionReason")).isEqualTo("Missing torque spec");
    }

    @Test
    void submitWithZeroStepsReturns422() {
        ResponseEntity<Map<String, Object>> create = restTemplate.exchange(
                BASE, HttpMethod.POST,
                jsonRequest(token(), Map.of("identifier", "WI-WF-ZEROSTEP", "title", "Empty")),
                new ParameterizedTypeReference<>() { });
        UUID id = UUID.fromString((String) create.getBody().get("id"));
        ResponseEntity<String> submit = restTemplate.exchange(
                BASE + "/" + id + "/submit", HttpMethod.POST,
                new HttpEntity<>(headers()), String.class);
        assertThat(submit.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void patchHeaderOnPendingReturns409() {
        UUID id = createWithStepAndSubmit("WI-WF-PENDING-EDIT");
        ResponseEntity<String> resp = restTemplate.exchange(
                BASE + "/" + id, HttpMethod.PATCH,
                jsonRequest(token(), Map.of("title", "New title")), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @SuppressWarnings("unchecked")
    void patchHeaderOnApprovedAutoCreatesDraftRevision() {
        UUID id = createWithStepAndSubmit("WI-WF-AUTODRAFT");
        restTemplate.exchange(BASE + "/" + id + "/approve", HttpMethod.POST,
                jsonRequest(token(), Map.of("password", GOOD_PASSWORD)),
                new ParameterizedTypeReference<Map<String, Object>>() { });

        ResponseEntity<Map<String, Object>> patched = restTemplate.exchange(
                BASE + "/" + id, HttpMethod.PATCH,
                jsonRequest(token(), Map.of("title", "Rev 1 title", "reasonForRevision", "update")),
                new ParameterizedTypeReference<>() { });
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patched.getBody().get("revision")).isEqualTo(1);
        assertThat(patched.getBody().get("revisionStatus")).isEqualTo("DRAFT");
        assertThat((List<?>) patched.getBody().get("steps")).hasSize(1);
    }

    @Test
    void deleteAfterApprovedReturns409() {
        UUID id = createWithStepAndSubmit("WI-WF-DEL-APPROVED");
        restTemplate.exchange(BASE + "/" + id + "/approve", HttpMethod.POST,
                jsonRequest(token(), Map.of("password", GOOD_PASSWORD)),
                new ParameterizedTypeReference<Map<String, Object>>() { });

        ResponseEntity<String> del = restTemplate.exchange(
                BASE + "/" + id, HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()), String.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token());
        return headers;
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(buildToken(ORG, List.of("SYSTEM_ADMIN")));
        return headers;
    }
}
