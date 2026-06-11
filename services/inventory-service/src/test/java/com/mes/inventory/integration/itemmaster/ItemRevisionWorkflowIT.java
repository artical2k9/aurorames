package com.mes.inventory.integration.itemmaster;

import com.mes.inventory.integration.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MES-114: End-to-end revision workflow tests for item master.
 * Covers DRAFT → PENDING_APPROVAL → APPROVED / REJECTED paths and hasDraft semantics.
 * Extends BaseIntegrationTest directly to avoid inheriting ItemMasterControllerIT @Test methods.
 */
class ItemRevisionWorkflowIT extends BaseIntegrationTest {

    private static final String ORG_ID   = "00000000-0000-0000-0000-000000000001";
    private static final String BASE_URL = "/api/v1/item-master";

    @Test
    void fullApprovalWorkflow_createSubmitApprove() {
        String eng = engineerToken();
        String admin = adminToken();
        String itemId = createItemAndGetId(eng, "WF-APPROVE-001");

        // Initial state: DRAFT at revision 0
        ResponseEntity<Map> initial = restTemplate.exchange(
                BASE_URL + "/" + itemId, HttpMethod.GET, bearerRequest(eng), Map.class);
        assertThat(initial.getBody()).extractingByKey("revisionStatus").isEqualTo("DRAFT");
        assertThat(initial.getBody()).extractingByKey("revision").isEqualTo(0);
        assertThat(initial.getBody()).extractingByKey("hasDraft").isEqualTo(true);

        // Submit
        submitDraft(eng, itemId);
        ResponseEntity<Map> pending = restTemplate.exchange(
                BASE_URL + "/" + itemId, HttpMethod.GET, bearerRequest(eng), Map.class);
        assertThat(pending.getBody()).extractingByKey("revisionStatus").isEqualTo("PENDING_APPROVAL");

        // Approve
        approveDraft(admin, itemId);
        ResponseEntity<Map> approved = restTemplate.exchange(
                BASE_URL + "/" + itemId, HttpMethod.GET, bearerRequest(eng), Map.class);
        assertThat(approved.getBody()).extractingByKey("revisionStatus").isEqualTo("APPROVED");
        assertThat(approved.getBody()).extractingByKey("hasDraft").isEqualTo(false);
        assertThat(approved.getBody().get("approvedBy")).isNotNull();
        assertThat(approved.getBody().get("approvedAt")).isNotNull();
    }

    @Test
    void rejectAndResubmit_workflowCompletesSuccessfully() {
        String eng = engineerToken();
        String admin = adminToken();
        String itemId = createItemAndGetId(eng, "WF-REJECT-RESUBMIT-001");

        submitDraft(eng, itemId);

        // Reject with reason
        ResponseEntity<Map> rejected = restTemplate.exchange(
                BASE_URL + "/" + itemId + "/reject",
                HttpMethod.POST,
                jsonRequest(admin, Map.of("rejectionReason", "Description too vague")),
                Map.class);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rejected.getBody()).extractingByKey("revisionStatus").isEqualTo("DRAFT");
        assertThat(rejected.getBody()).extractingByKey("rejectionReason").isEqualTo("Description too vague");
        assertThat(rejected.getBody().get("rejectedBy")).isNotNull();
        assertThat(rejected.getBody().get("rejectedAt")).isNotNull();
        assertThat(rejected.getBody()).extractingByKey("hasDraft").isEqualTo(true);

        // Edit the draft after rejection
        restTemplate.exchange(BASE_URL + "/" + itemId, HttpMethod.PATCH,
                jsonRequest(eng, Map.of("description", "Detailed description")), Map.class);

        // Re-submit and approve
        submitDraft(eng, itemId);
        approveDraft(admin, itemId);

        ResponseEntity<Map> finalState = restTemplate.exchange(
                BASE_URL + "/" + itemId, HttpMethod.GET, bearerRequest(eng), Map.class);
        assertThat(finalState.getBody()).extractingByKey("revisionStatus").isEqualTo("APPROVED");
    }

    @Test
    void rejectNonPendingRevision_returns409() {
        String eng = engineerToken();
        String admin = adminToken();
        String itemId = createItemAndGetId(eng, "WF-REJECT-NONPENDING-001");

        // Try to reject a DRAFT (not PENDING_APPROVAL)
        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL + "/" + itemId + "/reject",
                HttpMethod.POST,
                jsonRequest(admin, Map.of("rejectionReason", "Some reason")),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void patchApprovedItemCreatesDraftCoexistingWithApproved() {
        String eng = engineerToken();
        String admin = adminToken();
        String itemId = createItemAndGetId(eng, "WF-COEXIST-001");
        submitDraft(eng, itemId);
        approveDraft(admin, itemId);

        // Patch approved item — auto-creates DRAFT at revision 1
        ResponseEntity<Map> patched = restTemplate.exchange(
                BASE_URL + "/" + itemId, HttpMethod.PATCH,
                jsonRequest(eng, Map.of("description", "Rev 1 description")),
                Map.class);

        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patched.getBody()).extractingByKey("revision").isEqualTo(1);
        assertThat(patched.getBody()).extractingByKey("revisionStatus").isEqualTo("DRAFT");
        assertThat(patched.getBody()).extractingByKey("hasDraft").isEqualTo(true);

        // GET returns DRAFT (hasDraft=true shows draft exists; display prefers APPROVED by default)
        ResponseEntity<Map> display = restTemplate.exchange(
                BASE_URL + "/" + itemId, HttpMethod.GET, bearerRequest(eng), Map.class);
        assertThat(display.getBody()).extractingByKey("revisionStatus").isEqualTo("APPROVED");
        assertThat(display.getBody()).extractingByKey("hasDraft").isEqualTo(true);
    }

    @Test
    void cancelDraftAfterApproval_hasDraftFalse() {
        String eng = engineerToken();
        String admin = adminToken();
        String itemId = createItemAndGetId(eng, "WF-CANCEL-AFTER-APPROVE-001");
        submitDraft(eng, itemId);
        approveDraft(admin, itemId);

        // Auto-create draft
        restTemplate.exchange(BASE_URL + "/" + itemId, HttpMethod.PATCH,
                jsonRequest(eng, Map.of("description", "Draft to cancel")), Map.class);

        // Cancel the draft
        restTemplate.exchange(BASE_URL + "/" + itemId + "/draft",
                HttpMethod.DELETE, bearerRequest(eng), Void.class);

        // hasDraft should now be false; display revision is APPROVED
        ResponseEntity<Map> display = restTemplate.exchange(
                BASE_URL + "/" + itemId, HttpMethod.GET, bearerRequest(eng), Map.class);
        assertThat(display.getBody()).extractingByKey("revisionStatus").isEqualTo("APPROVED");
        assertThat(display.getBody()).extractingByKey("hasDraft").isEqualTo(false);
    }

    @Test
    void cancelDraftWhenNoDraftExists_returns409() {
        String eng = engineerToken();
        String admin = adminToken();
        String itemId = createItemAndGetId(eng, "WF-CANCEL-NODRAFT-001");
        submitDraft(eng, itemId);
        approveDraft(admin, itemId);

        // No draft exists — cancel should 409
        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL + "/" + itemId + "/draft",
                HttpMethod.DELETE, bearerRequest(eng), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void hasPendingApproval_trueWhenPendingCoexistsWithApproved() {
        String eng = engineerToken();
        String admin = adminToken();
        String itemId = createItemAndGetId(eng, "WF-PENDING-FLAG-001");
        submitDraft(eng, itemId);
        approveDraft(admin, itemId);

        // Create a new draft on top of the approved revision, then submit it
        restTemplate.exchange(BASE_URL + "/" + itemId, HttpMethod.PATCH,
                jsonRequest(eng, Map.of("description", "Rev 1 pending")), Map.class);
        submitDraft(eng, itemId);

        // Default GET returns APPROVED display; hasPendingApproval must be true
        ResponseEntity<Map> display = restTemplate.exchange(
                BASE_URL + "/" + itemId, HttpMethod.GET, bearerRequest(eng), Map.class);
        assertThat(display.getBody()).extractingByKey("revisionStatus").isEqualTo("APPROVED");
        assertThat(display.getBody()).extractingByKey("hasPendingApproval").isEqualTo(true);

        // GET with revisionStatus=PENDING_APPROVAL returns the pending revision
        ResponseEntity<Map> pending = restTemplate.exchange(
                BASE_URL + "/" + itemId + "?revisionStatus=PENDING_APPROVAL",
                HttpMethod.GET, bearerRequest(eng), Map.class);
        assertThat(pending.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pending.getBody()).extractingByKey("revisionStatus").isEqualTo("PENDING_APPROVAL");
        assertThat(pending.getBody()).extractingByKey("revision").isEqualTo(1);

        // After approval hasPendingApproval resets to false
        approveDraft(admin, itemId);
        ResponseEntity<Map> afterApprove = restTemplate.exchange(
                BASE_URL + "/" + itemId, HttpMethod.GET, bearerRequest(eng), Map.class);
        assertThat(afterApprove.getBody()).extractingByKey("revisionStatus").isEqualTo("APPROVED");
        assertThat(afterApprove.getBody()).extractingByKey("hasPendingApproval").isEqualTo(false);
    }

    @Test
    void getWithRevisionStatus_notFound_returns404() {
        String eng = engineerToken();
        String itemId = createItemAndGetId(eng, "WF-REVSTATUS-404-001");

        // Item only has DRAFT; requesting APPROVED should 404
        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL + "/" + itemId + "?revisionStatus=APPROVED",
                HttpMethod.GET, bearerRequest(eng), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String engineerToken() {
        return buildToken(ORG_ID, List.of("ENGINEER"));
    }

    private String adminToken() {
        return buildToken(ORG_ID, List.of("SYSTEM_ADMIN"));
    }

    private String createItemAndGetId(String token, String partNumber) {
        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                jsonRequest(token, baseItemRequest(partNumber, "IGNORED")), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String path = response.getHeaders().getLocation().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private void submitDraft(String token, String itemId) {
        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL + "/" + itemId + "/submit",
                HttpMethod.POST, bearerRequest(token), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void approveDraft(String token, String itemId) {
        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL + "/" + itemId + "/approve",
                HttpMethod.POST, bearerRequest(token), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
