package com.mes.inventory.integration.bom;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MES-114 Phase 7: BOM revision history endpoint tests.
 * Covers GET /api/v1/boms/{id}/revisions.
 */
class BomRevisionIT extends BomControllerIT {

    @Test
    void listBomRevisions_returnsAllRevisionsOrderedAsc() {
        String eng = engineerToken();
        String admin = sysAdminToken();
        Map<?, ?> parent = createItemBody(eng, "BOM-REV-HIST-001");
        String bomId = createBom(eng, itemId(parent));

        // Approve rev=0
        approveBom(eng, admin, bomId);

        // Patch header to create rev=1 DRAFT
        restTemplate.exchange(BOM_BASE + "/" + bomId, HttpMethod.PATCH,
                jsonRequest(eng, Map.of("description", "Rev 1 description")), Map.class);

        ResponseEntity<List> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/revisions",
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
        assertThat(rev0.get("bomRevisionId")).isNotNull();

        Map<?, ?> rev1 = (Map<?, ?>) revisions.get(1);
        assertThat(rev1.get("revision")).isEqualTo(1);
        assertThat(rev1.get("revisionStatus")).isEqualTo("DRAFT");
    }

    @Test
    void listBomRevisions_singleDraftRevision_returnsOneEntry() {
        String eng = engineerToken();
        Map<?, ?> parent = createItemBody(eng, "BOM-REV-SINGLE-001");
        String bomId = createBom(eng, itemId(parent));

        ResponseEntity<List> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/revisions",
                HttpMethod.GET, bearerRequest(eng), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        Map<?, ?> rev = (Map<?, ?>) response.getBody().get(0);
        assertThat(rev.get("revision")).isEqualTo(0);
        assertThat(rev.get("revisionStatus")).isEqualTo("DRAFT");
    }

    @Test
    void listBomRevisions_unauthenticated_returns401() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                BOM_BASE + "/00000000-0000-0000-0000-000000000099/revisions", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void listBomRevisions_unknownBom_returns404() {
        String eng = engineerToken();
        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE + "/00000000-0000-0000-0000-000000000099/revisions",
                HttpMethod.GET, bearerRequest(eng), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
