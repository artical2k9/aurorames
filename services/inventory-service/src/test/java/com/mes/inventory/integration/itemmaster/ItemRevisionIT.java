package com.mes.inventory.integration.itemmaster;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MES-114 Phase 7: revision history endpoint tests.
 * Covers GET /api/v1/item-master/{id}/revisions.
 */
class ItemRevisionIT extends ItemMasterControllerIT {

    @Test
    void listRevisions_returnsAllRevisionsOrderedAsc() {
        String eng = engineerToken();
        String admin = adminToken();
        String itemId = createItemAndGetId(eng, "REV-HIST-001");

        // Approve rev=0
        submitDraft(eng, itemId);
        approveDraft(admin, itemId);

        // Patch to create rev=1 DRAFT
        restTemplate.exchange(BASE_URL + "/" + itemId, HttpMethod.PATCH,
                jsonRequest(eng, Map.of("description", "Rev 1 update")), Map.class);

        ResponseEntity<List> response = restTemplate.exchange(
                BASE_URL + "/" + itemId + "/revisions",
                HttpMethod.GET, bearerRequest(eng), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> revisions = response.getBody();
        assertThat(revisions).hasSize(2);

        Map<?, ?> rev0 = (Map<?, ?>) revisions.get(0);
        assertThat(rev0.get("revision")).isEqualTo(0);
        assertThat(rev0.get("revisionStatus")).isEqualTo("APPROVED");
        assertThat(rev0.get("approvedBy")).isNotNull();
        assertThat(rev0.get("approvedAt")).isNotNull();
        assertThat(rev0.get("createdBy")).isNotNull();
        assertThat(rev0.get("createdAt")).isNotNull();
        assertThat(rev0.get("revisionId")).isNotNull();

        Map<?, ?> rev1 = (Map<?, ?>) revisions.get(1);
        assertThat(rev1.get("revision")).isEqualTo(1);
        assertThat(rev1.get("revisionStatus")).isEqualTo("DRAFT");
    }

    @Test
    void listRevisions_singleDraftRevision_returnsOneEntry() {
        String eng = engineerToken();
        String itemId = createItemAndGetId(eng, "REV-HIST-SINGLE-001");

        ResponseEntity<List> response = restTemplate.exchange(
                BASE_URL + "/" + itemId + "/revisions",
                HttpMethod.GET, bearerRequest(eng), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        Map<?, ?> rev = (Map<?, ?>) response.getBody().get(0);
        assertThat(rev.get("revision")).isEqualTo(0);
        assertThat(rev.get("revisionStatus")).isEqualTo("DRAFT");
    }

    @Test
    void listRevisions_unauthenticated_returns401() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                BASE_URL + "/00000000-0000-0000-0000-000000000099/revisions", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void listRevisions_unknownItem_returns404() {
        String eng = engineerToken();
        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL + "/00000000-0000-0000-0000-000000000099/revisions",
                HttpMethod.GET, bearerRequest(eng), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
