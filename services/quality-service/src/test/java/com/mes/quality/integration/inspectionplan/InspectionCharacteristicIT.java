package com.mes.quality.integration.inspectionplan;

import com.mes.quality.integration.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class InspectionCharacteristicIT extends BaseIntegrationTest {

    private static final String ORG_ID = "11111111-1111-1111-1111-111111111111";
    private static final String PLANS = "/api/v1/inspection-plans";

    @BeforeEach
    void resetStubs() {
        INVENTORY_WIREMOCK.resetAll();
    }

    private String adminToken() {
        return buildToken(ORG_ID, List.of("SYSTEM_ADMIN"));
    }

    @SuppressWarnings("unchecked")
    private String createPlan() {
        String itemId = UUID.randomUUID().toString();
        INVENTORY_WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/item-master/" + itemId))
                .willReturn(okJson("{\"id\":\"" + itemId + "\",\"partNumber\":\"PN-CHAR\"}")));
        ResponseEntity<Map> response = restTemplate.exchange(
                PLANS, HttpMethod.POST,
                jsonRequest(adminToken(), Map.of("itemId", itemId, "name", "Char plan")),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").toString();
    }

    private Map<String, Object> specific(int number, String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("characteristicNumber", number);
        body.put("name", name);
        body.put("source", "DESIGN");
        body.put("characteristicType", "SPECIFIC");
        body.put("sampleSizeRule", "ALL");
        body.put("nominalValue", 25.4);
        body.put("lowerLimit", 25.38);
        body.put("upperLimit", 25.42);
        return body;
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> addCharacteristic(String planId, Map<String, Object> body) {
        return restTemplate.exchange(PLANS + "/" + planId + "/characteristics", HttpMethod.POST,
                jsonRequest(adminToken(), body), Map.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    void addSpecificCommonCalculatedOnDraft() {
        String planId = createPlan();
        assertThat(addCharacteristic(planId, specific(10, "Bore")).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        Map<String, Object> common = new HashMap<>();
        common.put("characteristicNumber", 20);
        common.put("name", "Cleanliness");
        common.put("source", "IN_PROCESS");
        common.put("characteristicType", "COMMON");
        common.put("sampleSizeRule", "ALL");
        common.put("expectedBoolean", true);
        assertThat(addCharacteristic(planId, common).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> calc = new HashMap<>();
        calc.put("characteristicNumber", 30);
        calc.put("name", "Derived");
        calc.put("source", "DESIGN");
        calc.put("characteristicType", "CALCULATED");
        calc.put("sampleSizeRule", "ALL");
        calc.put("expression", "C10 * 2");
        assertThat(addCharacteristic(planId, calc).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @SuppressWarnings("unchecked")
    @Test
    void calculatedWithBadReferenceRejected422() {
        String planId = createPlan();
        Map<String, Object> calc = new HashMap<>();
        calc.put("characteristicNumber", 30);
        calc.put("name", "Derived");
        calc.put("source", "DESIGN");
        calc.put("characteristicType", "CALCULATED");
        calc.put("sampleSizeRule", "ALL");
        calc.put("expression", "C99 + 1");
        assertThat(addCharacteristic(planId, calc).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @SuppressWarnings("unchecked")
    @Test
    void duplicateCharacteristicNumberReturns409() {
        String planId = createPlan();
        addCharacteristic(planId, specific(10, "First"));
        assertThat(addCharacteristic(planId, specific(10, "Dup")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @SuppressWarnings("unchecked")
    @Test
    void editBlockedOnNonDraft409() {
        String planId = createPlan();
        addCharacteristic(planId, specific(10, "Bore"));
        restTemplate.exchange(PLANS + "/" + planId + "/submit", HttpMethod.POST,
                jsonRequest(adminToken(), Map.of()), Map.class);
        // PENDING_APPROVAL → characteristics cannot be added.
        assertThat(addCharacteristic(planId, specific(20, "Late")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @SuppressWarnings("unchecked")
    @Test
    void deleteReferencedCharacteristicReturns409NamingDependents() {
        String planId = createPlan();
        addCharacteristic(planId, specific(10, "Bore"));
        Map<String, Object> calc = new HashMap<>();
        calc.put("characteristicNumber", 30);
        calc.put("name", "Derived");
        calc.put("source", "DESIGN");
        calc.put("characteristicType", "CALCULATED");
        calc.put("sampleSizeRule", "ALL");
        calc.put("expression", "C10 * 2");
        addCharacteristic(planId, calc);

        // Find C10's id from the list.
        ResponseEntity<List> list = restTemplate.exchange(
                PLANS + "/" + planId + "/characteristics", HttpMethod.GET,
                jsonRequest(adminToken(), Map.of()), List.class);
        Map<String, Object> c10 = ((List<Map<String, Object>>) list.getBody()).stream()
                .filter(c -> Integer.valueOf(10).equals(c.get("characteristicNumber")))
                .findFirst().orElseThrow();

        ResponseEntity<Map> deleteResp = restTemplate.exchange(
                PLANS + "/" + planId + "/characteristics/" + c10.get("id"), HttpMethod.DELETE,
                jsonRequest(adminToken(), Map.of()), Map.class);
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(deleteResp.getBody().get("error").toString()).contains("C30");
    }

    @SuppressWarnings("unchecked")
    @Test
    void copyOnRevisionCopiesCharacteristics() {
        String planId = createPlan();
        addCharacteristic(planId, specific(10, "Bore"));
        restTemplate.exchange(PLANS + "/" + planId + "/submit", HttpMethod.POST,
                jsonRequest(adminToken(), Map.of()), Map.class);
        restTemplate.exchange(PLANS + "/" + planId + "/approve", HttpMethod.POST,
                jsonRequest(adminToken(), Map.of()), Map.class);
        // Patch header → auto-draft rev 1 with copied characteristics.
        restTemplate.exchange(PLANS + "/" + planId, HttpMethod.PATCH,
                jsonRequest(adminToken(), Map.of("description", "Rev 1")), Map.class);

        ResponseEntity<List> rev1Chars = restTemplate.exchange(
                PLANS + "/" + planId + "/characteristics?revisionNumber=1", HttpMethod.GET,
                jsonRequest(adminToken(), Map.of()), List.class);
        assertThat(rev1Chars.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rev1Chars.getBody()).hasSize(1);
    }
}
