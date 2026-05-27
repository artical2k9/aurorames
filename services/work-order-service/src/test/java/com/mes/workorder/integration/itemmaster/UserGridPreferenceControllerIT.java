package com.mes.workorder.integration.itemmaster;

import com.mes.workorder.integration.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UserGridPreferenceControllerIT extends BaseIntegrationTest {

    static final String BASE_URL = "/api/v1/users/preferences/grid";
    static final String ORG_ID = "00000000-0000-0000-0000-000000000001";
    static final String MODULE = "ITEM_MASTER";

    @Test
    void getWithNoSavedConfigReturnsDefaultColumnList() {
        String token = engineerToken();
        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL + "/" + MODULE, HttpMethod.GET, bearerRequest(token), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("columns");
        assertThat((List<?>) response.getBody().get("columns")).isNotEmpty();
    }

    @Test
    void putSavesConfigAndSubsequentGetReturnsSavedConfig() {
        String token = engineerToken();
        List<Map<String, Object>> columns = List.of(
                Map.of("columnKey", "partNumber", "visible", true, "order", 0),
                Map.of("columnKey", "description", "visible", false, "order", 1)
        );
        Map<String, Object> body = Map.of("columns", columns);

        ResponseEntity<Map> putResponse = restTemplate.exchange(
                BASE_URL + "/" + MODULE, HttpMethod.PUT, jsonRequest(token, body), Map.class);
        assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> getResponse = restTemplate.exchange(
                BASE_URL + "/" + MODULE, HttpMethod.GET, bearerRequest(token), Map.class);
        List<?> savedColumns = (List<?>) getResponse.getBody().get("columns");
        assertThat(savedColumns).hasSize(2);
    }

    @Test
    void differentModuleKeyHasIndependentConfig() {
        String token = engineerToken();
        List<Map<String, Object>> columns = List.of(
                Map.of("columnKey", "partNumber", "visible", true, "order", 0));
        restTemplate.exchange(BASE_URL + "/" + MODULE, HttpMethod.PUT,
                jsonRequest(token, Map.of("columns", columns)), Map.class);

        ResponseEntity<Map> otherModule = restTemplate.exchange(
                BASE_URL + "/WORK_ORDER", HttpMethod.GET, bearerRequest(token), Map.class);
        assertThat(otherModule.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void differentJwtSubHasIndependentConfig() {
        String token1 = engineerToken();
        String token2 = engineerToken();

        List<Map<String, Object>> columns = List.of(
                Map.of("columnKey", "partNumber", "visible", true, "order", 0));
        restTemplate.exchange(BASE_URL + "/" + MODULE, HttpMethod.PUT,
                jsonRequest(token1, Map.of("columns", columns)), Map.class);

        ResponseEntity<Map> user2Response = restTemplate.exchange(
                BASE_URL + "/" + MODULE, HttpMethod.GET, bearerRequest(token2), Map.class);
        assertThat(user2Response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String engineerToken() {
        return buildToken(ORG_ID, List.of("ENGINEER"));
    }

    private HttpEntity<Void> bearerRequest(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return new HttpEntity<>(h);
    }

    private HttpEntity<Map<String, Object>> jsonRequest(String token, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }
}
