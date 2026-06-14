package com.mes.engineering.integration.workinstruction;

import com.mes.engineering.integration.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkInstructionControllerIT extends BaseIntegrationTest {

    private static final String ORG_A = "11111111-1111-1111-1111-111111111111";
    private static final String ORG_B = "22222222-2222-2222-2222-222222222222";
    private static final String BASE = "/api/v1/work-instructions";

    private String engineerTokenA() {
        return buildToken(ORG_A, List.of("ENGINEER"));
    }

    @SuppressWarnings("unchecked")
    private UUID createInstruction(String identifier) {
        Map<String, Object> body = Map.of(
                "identifier", identifier,
                "title", "Deburr bracket",
                "description", "How to deburr");
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                BASE, HttpMethod.POST, jsonRequest(engineerTokenA(), body),
                new ParameterizedTypeReference<>() { });
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString((String) resp.getBody().get("id"));
    }

    @Test
    void createReturns201WithRevisionZeroDraft() {
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                BASE, HttpMethod.POST,
                jsonRequest(engineerTokenA(), Map.of("identifier", "WI-CREATE-1", "title", "T")),
                new ParameterizedTypeReference<>() { });
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("revision")).isEqualTo(0);
        assertThat(resp.getBody().get("revisionStatus")).isEqualTo("DRAFT");
        assertThat(resp.getBody().get("hasDraft")).isEqualTo(true);
    }

    @Test
    void duplicateIdentifierReturns409() {
        createInstruction("WI-DUP-1");
        ResponseEntity<String> resp = restTemplate.exchange(
                BASE, HttpMethod.POST,
                jsonRequest(engineerTokenA(), Map.of("identifier", "WI-DUP-1", "title", "T2")),
                String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void identifierSuggestionReturnsNextSequence() {
        createInstruction("PROC-001");
        ResponseEntity<String> resp = restTemplate.exchange(
                BASE + "/identifier-suggestion?prefix=PROC", HttpMethod.GET,
                new HttpEntity<>(authHeaders(engineerTokenA())), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEqualTo("PROC-002");
    }

    @Test
    void listAndSearchReturnInstruction() {
        createInstruction("WI-LIST-SEARCHABLE");
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                BASE + "?search=SEARCHABLE", HttpMethod.GET,
                new HttpEntity<>(authHeaders(engineerTokenA())),
                new ParameterizedTypeReference<>() { });
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> content = (List<?>) resp.getBody().get("content");
        assertThat(content).hasSize(1);
    }

    @Test
    void getByRevisionNumberReturnsThatRevision() {
        UUID id = createInstruction("WI-GET-REV");
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                BASE + "/" + id + "?revisionNumber=0", HttpMethod.GET,
                new HttpEntity<>(authHeaders(engineerTokenA())),
                new ParameterizedTypeReference<>() { });
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("revision")).isEqualTo(0);
    }

    @Test
    void otherOrgCannotSeeInstruction404() {
        UUID id = createInstruction("WI-ORG-ISO");
        ResponseEntity<String> resp = restTemplate.exchange(
                BASE + "/" + id, HttpMethod.GET,
                new HttpEntity<>(authHeaders(buildToken(ORG_B, List.of("ENGINEER")))),
                String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unauthenticatedReturns401() {
        ResponseEntity<String> resp = restTemplate.exchange(
                BASE, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void roleWithoutPrivilegeReturns403() {
        ResponseEntity<String> resp = restTemplate.exchange(
                BASE, HttpMethod.GET,
                new HttpEntity<>(authHeaders(buildToken(ORG_A, List.of("OPERATOR")))),
                String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deleteNeverApprovedReturns204ThenGone() {
        UUID id = createInstruction("WI-DEL-1");
        ResponseEntity<Void> del = restTemplate.exchange(
                BASE + "/" + id, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(buildToken(ORG_A, List.of("SYSTEM_ADMIN")))), Void.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> get = restTemplate.exchange(
                BASE + "/" + id, HttpMethod.GET,
                new HttpEntity<>(authHeaders(engineerTokenA())), String.class);
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }
}
