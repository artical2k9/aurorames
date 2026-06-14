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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.MinIOContainer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkInstructionMediaIT extends BaseIntegrationTest {

    private static final String ORG = "55555555-5555-5555-5555-555555555555";
    private static final String BASE = "/api/v1/work-instructions";

    private static final MinIOContainer MINIO =
            new MinIOContainer("minio/minio:RELEASE.2024-10-13T13-34-11Z");

    static {
        MINIO.start();
    }

    @DynamicPropertySource
    static void mediaProps(DynamicPropertyRegistry registry) {
        registry.add("mes.wi.media.endpoint", MINIO::getS3URL);
        registry.add("mes.wi.media.access-key", MINIO::getUserName);
        registry.add("mes.wi.media.secret-key", MINIO::getPassword);
        registry.add("mes.wi.media.bucket", () -> "wi-media-test");
        // Small image limit so an oversize case does not require a large payload.
        registry.add("mes.wi.media.max-image-bytes", () -> "50");
    }

    private String token() {
        return buildToken(ORG, List.of("ENGINEER"));
    }

    @SuppressWarnings("unchecked")
    private UUID createInstructionWithStep(String identifier) {
        ResponseEntity<Map<String, Object>> create = restTemplate.exchange(
                BASE, HttpMethod.POST,
                jsonRequest(token(), Map.of("identifier", identifier, "title", "Media WI")),
                new ParameterizedTypeReference<>() { });
        UUID id = UUID.fromString((String) create.getBody().get("id"));
        restTemplate.exchange(BASE + "/" + id + "/steps", HttpMethod.POST,
                jsonRequest(token(), Map.of("title", "Step", "bodyHtml", "<p>x</p>")),
                new ParameterizedTypeReference<Map<String, Object>>() { });
        return id;
    }

    @SuppressWarnings("unchecked")
    private UUID firstStepId(UUID wiId) {
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                BASE + "/" + wiId, HttpMethod.GET, new HttpEntity<>(bearer(token())),
                new ParameterizedTypeReference<>() { });
        List<Map<String, Object>> steps = (List<Map<String, Object>>) resp.getBody().get("steps");
        return UUID.fromString((String) steps.get(0).get("id"));
    }

    private ResponseEntity<Map<String, Object>> uploadFile(UUID wiId, UUID stepId, String filename,
                                                           String contentType, byte[] bytes) {
        HttpHeaders headers = bearer(token());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        org.springframework.core.io.ByteArrayResource resource =
                new org.springframework.core.io.ByteArrayResource(bytes) {
                    @Override
                    public String getFilename() {
                        return filename;
                    }
                };
        form.add("file", new HttpEntity<>(resource, fileHeaders(contentType)));
        return restTemplate.exchange(BASE + "/" + wiId + "/steps/" + stepId + "/media",
                HttpMethod.POST, new HttpEntity<>(form, headers),
                new ParameterizedTypeReference<>() { });
    }

    @Test
    void uploadPngReturns201WithMetadata() {
        UUID wi = createInstructionWithStep("WI-MEDIA-PNG");
        UUID step = firstStepId(wi);
        ResponseEntity<Map<String, Object>> resp =
                uploadFile(wi, step, "photo.png", "image/png", new byte[]{1, 2, 3});
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("contentType")).isEqualTo("image/png");
        assertThat(resp.getBody().get("fileName")).isEqualTo("photo.png");
    }

    @Test
    void uploadPdfReturns201() {
        UUID wi = createInstructionWithStep("WI-MEDIA-PDF");
        UUID step = firstStepId(wi);
        ResponseEntity<Map<String, Object>> resp =
                uploadFile(wi, step, "spec.pdf", "application/pdf", new byte[]{37, 80, 68, 70});
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void oversizeReturns422() {
        UUID wi = createInstructionWithStep("WI-MEDIA-BIG");
        UUID step = firstStepId(wi);
        ResponseEntity<Map<String, Object>> resp =
                uploadFile(wi, step, "big.png", "image/png", new byte[100]);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void unsupportedTypeReturns422() {
        UUID wi = createInstructionWithStep("WI-MEDIA-BADTYPE");
        UUID step = firstStepId(wi);
        ResponseEntity<Map<String, Object>> resp =
                uploadFile(wi, step, "x.exe", "application/octet-stream", new byte[]{1});
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void downloadStreamsBinaryAndRequiresAuth() {
        UUID wi = createInstructionWithStep("WI-MEDIA-DL");
        UUID step = firstStepId(wi);
        UUID attachmentId = UUID.fromString((String)
                uploadFile(wi, step, "img.png", "image/png", new byte[]{9, 8, 7}).getBody().get("id"));

        ResponseEntity<byte[]> ok = restTemplate.exchange(
                BASE + "/media/" + attachmentId, HttpMethod.GET,
                new HttpEntity<>(bearer(token())), byte[].class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody()).containsExactly(9, 8, 7);
        assertThat(ok.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);

        ResponseEntity<String> noAuth = restTemplate.exchange(
                BASE + "/media/" + attachmentId, HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), String.class);
        assertThat(noAuth.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void patchCaptionAndOrder() {
        UUID wi = createInstructionWithStep("WI-MEDIA-PATCH");
        UUID step = firstStepId(wi);
        UUID attachmentId = UUID.fromString((String)
                uploadFile(wi, step, "img.png", "image/png", new byte[]{1}).getBody().get("id"));

        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                BASE + "/" + wi + "/steps/" + step + "/media/" + attachmentId, HttpMethod.PATCH,
                jsonRequest(token(), Map.of("caption", "Front view", "displayOrder", 5)),
                new ParameterizedTypeReference<>() { });
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("caption")).isEqualTo("Front view");
        assertThat(resp.getBody().get("displayOrder")).isEqualTo(5);
    }

    @Test
    void deleteRemovesAttachment() {
        UUID wi = createInstructionWithStep("WI-MEDIA-DEL");
        UUID step = firstStepId(wi);
        UUID attachmentId = UUID.fromString((String)
                uploadFile(wi, step, "img.png", "image/png", new byte[]{1}).getBody().get("id"));

        ResponseEntity<Void> del = restTemplate.exchange(
                BASE + "/" + wi + "/steps/" + step + "/media/" + attachmentId, HttpMethod.DELETE,
                new HttpEntity<>(bearer(token())), Void.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> get = restTemplate.exchange(
                BASE + "/media/" + attachmentId, HttpMethod.GET,
                new HttpEntity<>(bearer(token())), String.class);
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpHeaders fileHeaders(String contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        return headers;
    }
}
