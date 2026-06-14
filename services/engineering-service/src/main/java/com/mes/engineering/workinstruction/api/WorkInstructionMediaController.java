package com.mes.engineering.workinstruction.api;

import com.mes.common.security.annotation.RequiresPrivilege;
import com.mes.engineering.workinstruction.api.dto.MediaAttachmentDto;
import com.mes.engineering.workinstruction.api.dto.PatchMediaRequest;
import com.mes.engineering.workinstruction.domain.WorkInstructionRevision;
import com.mes.engineering.workinstruction.service.MediaDownload;
import com.mes.engineering.workinstruction.service.MediaService;
import com.mes.engineering.workinstruction.service.WorkInstructionService;
import com.mes.udf.api.JwtClaimsExtractor;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work-instructions")
public class WorkInstructionMediaController {

    private final WorkInstructionService wiService;
    private final MediaService mediaService;

    public WorkInstructionMediaController(WorkInstructionService wiService, MediaService mediaService) {
        this.wiService = wiService;
        this.mediaService = mediaService;
    }

    @PostMapping(path = "/{id}/steps/{stepId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPrivilege("engineering:work-instruction:update")
    public ResponseEntity<MediaAttachmentDto> upload(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @PathVariable UUID stepId,
            @RequestParam("file") MultipartFile file) {
        WorkInstructionRevision draft = wiService.requireEditableDraft(JwtClaimsExtractor.orgId(jwt), id);
        return ResponseEntity.status(201).body(mediaService.upload(draft, stepId, file));
    }

    @GetMapping("/media/{attachmentId}")
    @RequiresPrivilege("engineering:work-instruction:read")
    public ResponseEntity<StreamingResponseBody> download(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID attachmentId) {
        MediaDownload meta = mediaService.resolveDownload(JwtClaimsExtractor.orgId(jwt), attachmentId);
        StreamingResponseBody body = out -> {
            try (InputStream in = mediaService.open(meta.objectKey()).get()) {
                in.transferTo(out);
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + meta.fileName() + "\"")
                .contentType(MediaType.parseMediaType(meta.contentType()))
                .contentLength(meta.sizeBytes())
                .body(body);
    }

    @PatchMapping("/{id}/steps/{stepId}/media/{attachmentId}")
    @RequiresPrivilege("engineering:work-instruction:update")
    public MediaAttachmentDto patch(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @PathVariable UUID stepId,
            @PathVariable UUID attachmentId,
            @Valid @RequestBody PatchMediaRequest request) {
        WorkInstructionRevision draft = wiService.requireEditableDraft(JwtClaimsExtractor.orgId(jwt), id);
        return mediaService.patch(draft, stepId, attachmentId,
                request.getCaption(), request.getDisplayOrder());
    }

    @DeleteMapping("/{id}/steps/{stepId}/media/{attachmentId}")
    @RequiresPrivilege("engineering:work-instruction:update")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @PathVariable UUID stepId,
            @PathVariable UUID attachmentId) {
        WorkInstructionRevision draft = wiService.requireEditableDraft(JwtClaimsExtractor.orgId(jwt), id);
        mediaService.delete(draft, stepId, attachmentId);
        return ResponseEntity.noContent().build();
    }
}
