package com.mes.workorder.integration.udf;

import com.mes.workorder.integration.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ItemMasterWithUdfIT extends BaseIntegrationTest {

    static final String ORG_ID = "00000000-0000-0000-0000-000000000001";
    static final String UDF_URL = "/api/v1/udf/fields";
    static final String ITEM_URL = "/api/v1/item-master";

    @Test
    void requiredUdfMissingOnCreateReturns422() {
        String adminToken = adminToken();
        restTemplate.exchange(UDF_URL, HttpMethod.POST,
                jsonRequest(adminToken, requiredTextField("drawing_ref_a")), Map.class);

        String engineerToken = engineerToken();
        ResponseEntity<Map> response = restTemplate.exchange(
                ITEM_URL, HttpMethod.POST,
                jsonRequest(engineerToken, baseItemRequest("BRKT-UDF01", "A")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        String body = response.getBody().toString();
        assertThat(body).contains("drawing_ref_a");
    }

    @Test
    void presentUdfReturns201AndGetReturnsCustomFields() {
        String adminToken = adminToken();
        restTemplate.exchange(UDF_URL, HttpMethod.POST,
                jsonRequest(adminToken, requiredTextField("drawing_ref_b")), Map.class);

        String engineerToken = engineerToken();
        Map<String, Object> req = baseItemRequest("BRKT-UDF02", "A");
        req.put("customFields", Map.of("drawing_ref_b", "DRW-001"));
        ResponseEntity<Map> created = restTemplate.exchange(
                ITEM_URL, HttpMethod.POST, jsonRequest(engineerToken, req), Map.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String location = created.getHeaders().getLocation().getPath();
        String itemId = location.substring(location.lastIndexOf('/') + 1);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(engineerToken);
        ResponseEntity<Map> retrieved = restTemplate.exchange(
                ITEM_URL + "/" + itemId, HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

        assertThat(retrieved.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> customFields = (Map<String, Object>) retrieved.getBody().get("customFields");
        assertThat(customFields).containsEntry("drawing_ref_b", "DRW-001");
    }

    @Test
    void listTypeInvalidOptionReturns422() {
        String adminToken = adminToken();
        Map<String, Object> listReq = new HashMap<>(Map.of(
                "moduleKey", "ITEM_MASTER",
                "fieldKey", "material_standard_d",
                "label", "Material Standard",
                "fieldType", "LIST",
                "listOptions", List.of("AMS2750", "AMS4000", "ASTM-B209")
        ));
        restTemplate.exchange(UDF_URL, HttpMethod.POST, jsonRequest(adminToken, listReq), Map.class);

        String engineerToken = engineerToken();
        Map<String, Object> req = baseItemRequest("BRKT-UDF04", "A");
        req.put("customFields", Map.of("material_standard_d", "ISO-9001"));
        ResponseEntity<Map> response = restTemplate.exchange(
                ITEM_URL, HttpMethod.POST, jsonRequest(engineerToken, req), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().get("message").toString())
                .contains("material_standard_d");
    }

    @Test
    void numberRangeViolationReturns422() {
        String adminToken = adminToken();
        Map<String, Object> numReq = new HashMap<>(Map.of(
                "moduleKey", "ITEM_MASTER",
                "fieldKey", "thickness_c",
                "label", "Thickness mm",
                "fieldType", "NUMBER",
                "required", false,
                "validationRules", Map.of("min", 0, "max", 50)
        ));
        restTemplate.exchange(UDF_URL, HttpMethod.POST, jsonRequest(adminToken, numReq), Map.class);

        String engineerToken = engineerToken();
        Map<String, Object> req = baseItemRequest("BRKT-UDF03", "A");
        req.put("customFields", Map.of("thickness_c", 200));
        ResponseEntity<Map> response = restTemplate.exchange(
                ITEM_URL, HttpMethod.POST, jsonRequest(engineerToken, req), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String adminToken() {
        return buildToken(ORG_ID, List.of("SYSTEM_ADMIN"));
    }

    private String engineerToken() {
        return buildToken(ORG_ID, List.of("ENGINEER"));
    }

    private Map<String, Object> requiredTextField(String fieldKey) {
        return Map.of(
                "moduleKey", "ITEM_MASTER",
                "fieldKey", fieldKey,
                "label", "Field " + fieldKey,
                "fieldType", "TEXT",
                "required", true
        );
    }

}
