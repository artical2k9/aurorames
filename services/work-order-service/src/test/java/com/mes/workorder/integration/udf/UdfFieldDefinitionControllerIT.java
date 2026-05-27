package com.mes.workorder.integration.udf;

import com.mes.workorder.integration.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UdfFieldDefinitionControllerIT extends BaseIntegrationTest {

    static final String ORG_ID = "00000000-0000-0000-0000-000000000001";
    static final String UDF_URL = "/udf/fields";
    static final String ITEM_URL = "/api/v1/item-master";

    @Test
    void postDefinesFieldAndReturns201() {
        String token = adminToken();
        ResponseEntity<Map> response = restTemplate.exchange(
                UDF_URL, HttpMethod.POST,
                jsonRequest(token, textFieldRequest("code_ref_001", false)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("id");
    }

    @Test
    void getListsFieldsByModule() {
        String token = adminToken();
        restTemplate.exchange(UDF_URL, HttpMethod.POST,
                jsonRequest(token, textFieldRequest("code_ref_002", false)), Map.class);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                UDF_URL + "?module=ITEM_MASTER", HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        assertThat(response.getBody())
                .anyMatch(f -> "code_ref_002".equals(f.get("fieldKey")));
    }

    @Test
    void getWithInvalidModuleReturns400() {
        String token = adminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Map> response = restTemplate.exchange(
                UDF_URL + "?module=NOT_A_MODULE", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void engineerPostReturns403() {
        String engineerToken = engineerToken();

        ResponseEntity<Map> response = restTemplate.exchange(
                UDF_URL, HttpMethod.POST,
                jsonRequest(engineerToken, textFieldRequest("code_ref_003", false)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void duplicateFieldKeyReturns409AndErrorResponseShape() {
        String token = adminToken();
        Map<String, Object> req = textFieldRequest("code_ref_004", false);
        restTemplate.exchange(UDF_URL, HttpMethod.POST, jsonRequest(token, req), Map.class);

        ResponseEntity<Map> response = restTemplate.exchange(
                UDF_URL, HttpMethod.POST, jsonRequest(token, req), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsKeys("status", "error", "message", "timestamp");
        assertThat(response.getBody().get("status")).isEqualTo(409);
    }

    @Test
    void listTypeWithoutOptionsReturns422() {
        String token = adminToken();
        Map<String, Object> req = Map.of(
                "moduleKey", "ITEM_MASTER",
                "fieldKey", "material_no_opts",
                "label", "Material",
                "fieldType", "LIST"
        );
        ResponseEntity<Map> response = restTemplate.exchange(
                UDF_URL, HttpMethod.POST, jsonRequest(token, req), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void deleteWithValuesWithoutForceReturns409() {
        String adminToken = adminToken();
        ResponseEntity<Map> created = restTemplate.exchange(
                UDF_URL, HttpMethod.POST,
                jsonRequest(adminToken, textFieldRequest("code_ref_005", false)),
                Map.class);
        String fieldId = (String) created.getBody().get("id");

        String engineerToken = engineerToken();
        Map<String, Object> itemReq = itemRequest("BRKT-DEL05", "A", Map.of("code_ref_005", "REF-001"));
        restTemplate.exchange(ITEM_URL, HttpMethod.POST, jsonRequest(engineerToken, itemReq), Map.class);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        ResponseEntity<Map> response = restTemplate.exchange(
                UDF_URL + "/" + fieldId, HttpMethod.DELETE,
                new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("message").toString())
                .contains("record(s) have values");
    }

    @Test
    void deleteNonExistentFieldReturns404() {
        String adminToken = adminToken();
        String nonExistentId = UUID.randomUUID().toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        ResponseEntity<Map> response = restTemplate.exchange(
                UDF_URL + "/" + nonExistentId, HttpMethod.DELETE,
                new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsKeys("status", "error", "message", "timestamp");
    }

    @Test
    void deleteWithForceReturns204() {
        String adminToken = adminToken();
        ResponseEntity<Map> created = restTemplate.exchange(
                UDF_URL, HttpMethod.POST,
                jsonRequest(adminToken, textFieldRequest("code_ref_006", false)),
                Map.class);
        String fieldId = (String) created.getBody().get("id");

        String engineerToken = engineerToken();
        restTemplate.exchange(ITEM_URL, HttpMethod.POST,
                jsonRequest(engineerToken, itemRequest("BRKT-DEL06", "A", Map.of("code_ref_006", "VALUE"))),
                Map.class);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        ResponseEntity<Void> response = restTemplate.exchange(
                UDF_URL + "/" + fieldId + "?force=true", HttpMethod.DELETE,
                new HttpEntity<>(headers), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String adminToken() {
        return buildToken(ORG_ID, List.of("SYSTEM_ADMIN"));
    }

    private String engineerToken() {
        return buildToken(ORG_ID, List.of("ENGINEER"));
    }

    private Map<String, Object> textFieldRequest(String fieldKey, boolean required) {
        return Map.of(
                "moduleKey", "ITEM_MASTER",
                "fieldKey", fieldKey,
                "label", "Field " + fieldKey,
                "fieldType", "TEXT",
                "required", required
        );
    }

    private Map<String, Object> itemRequest(String partNumber, String revision,
                                            Map<String, Object> customFields) {
        var req = new HashMap<String, Object>(Map.of(
                "partNumber", partNumber,
                "revision", revision,
                "description", "Test bracket",
                "unitOfMeasure", "EA",
                "cageCode", "CAGE01",
                "classification", "FABRICATED",
                "makeBuyCode", "MAKE",
                "traceabilityMethod", "SERIAL"
        ));
        req.put("customFields", customFields);
        return req;
    }

    private HttpEntity<Map<String, Object>> jsonRequest(String token, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }
}
