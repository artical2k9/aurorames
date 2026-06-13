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

class WorkInstructionStepIT extends BaseIntegrationTest {

    private static final String ORG = "33333333-3333-3333-3333-333333333333";
    private static final String BASE = "/api/v1/work-instructions";

    private String token() {
        return buildToken(ORG, List.of("ENGINEER"));
    }

    @SuppressWarnings("unchecked")
    private UUID createInstruction(String id) {
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                BASE, HttpMethod.POST,
                jsonRequest(token(), Map.of("identifier", id, "title", "Steps test")),
                new ParameterizedTypeReference<>() { });
        return UUID.fromString((String) resp.getBody().get("id"));
    }

    @SuppressWarnings("unchecked")
    private UUID addStep(UUID wiId, String title) {
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                BASE + "/" + wiId + "/steps", HttpMethod.POST,
                jsonRequest(token(), Map.of("title", title, "bodyHtml", "<p>" + title + "</p>")),
                new ParameterizedTypeReference<>() { });
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString((String) resp.getBody().get("id"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> stepsOf(UUID wiId) {
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                BASE + "/" + wiId, HttpMethod.GET, new HttpEntity<>(headers()),
                new ParameterizedTypeReference<>() { });
        return (List<Map<String, Object>>) resp.getBody().get("steps");
    }

    @Test
    void addsStepsReturnedInOrder() {
        UUID id = createInstruction("WI-STEP-ORDER");
        addStep(id, "First");
        addStep(id, "Second");
        addStep(id, "Third");
        List<Map<String, Object>> steps = stepsOf(id);
        assertThat(steps).hasSize(3);
        assertThat(steps).extracting(s -> s.get("title"))
                .containsExactly("First", "Second", "Third");
        assertThat(steps).extracting(s -> s.get("stepNumber")).containsExactly(10, 20, 30);
    }

    @Test
    void patchStepUpdatesTitleAndSanitisesBody() {
        UUID id = createInstruction("WI-STEP-PATCH");
        UUID stepId = addStep(id, "Original");
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                BASE + "/" + id + "/steps/" + stepId, HttpMethod.PATCH,
                jsonRequest(token(), Map.of("title", "Updated",
                        "bodyHtml", "<p>ok</p><script>bad()</script>")),
                new ParameterizedTypeReference<>() { });
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("title")).isEqualTo("Updated");
        assertThat((String) resp.getBody().get("bodyHtml")).doesNotContain("script");
    }

    @Test
    void deleteStepRemovesIt() {
        UUID id = createInstruction("WI-STEP-DEL");
        UUID stepId = addStep(id, "Doomed");
        ResponseEntity<Void> del = restTemplate.exchange(
                BASE + "/" + id + "/steps/" + stepId, HttpMethod.DELETE,
                new HttpEntity<>(headers()), Void.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(stepsOf(id)).isEmpty();
    }

    @Test
    void reorderReassignsStepNumbers() {
        UUID id = createInstruction("WI-STEP-REORDER");
        UUID s1 = addStep(id, "A");
        UUID s2 = addStep(id, "B");
        UUID s3 = addStep(id, "C");
        ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                BASE + "/" + id + "/steps/reorder", HttpMethod.POST,
                jsonRequest(token(), Map.of("orderedStepIds", List.of(s3, s1, s2))),
                new ParameterizedTypeReference<>() { });
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).extracting(s -> s.get("title")).containsExactly("C", "A", "B");
    }

    @Test
    void stepOperationsOnPendingRevisionReturn409() {
        UUID id = createInstruction("WI-STEP-PENDING");
        addStep(id, "Only step");
        ResponseEntity<Map<String, Object>> submit = restTemplate.exchange(
                BASE + "/" + id + "/submit", HttpMethod.POST,
                new HttpEntity<>(headers()), new ParameterizedTypeReference<>() { });
        assertThat(submit.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(submit.getBody().get("revisionStatus")).isEqualTo("PENDING_APPROVAL");

        ResponseEntity<String> resp = restTemplate.exchange(
                BASE + "/" + id + "/steps", HttpMethod.POST,
                jsonRequest(token(), Map.of("title", "late", "bodyHtml", "<p>x</p>")),
                String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token());
        return headers;
    }
}
