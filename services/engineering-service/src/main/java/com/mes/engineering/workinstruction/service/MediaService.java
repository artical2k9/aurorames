package com.mes.engineering.workinstruction.service;

import com.mes.engineering.workinstruction.api.dto.MediaAttachmentDto;
import com.mes.engineering.workinstruction.domain.MediaAttachment;
import com.mes.engineering.workinstruction.domain.RevisionStatus;
import com.mes.engineering.workinstruction.domain.WorkInstructionRevision;
import com.mes.engineering.workinstruction.domain.WorkInstructionStep;
import com.mes.engineering.workinstruction.repository.MediaAttachmentRepository;
import com.mes.engineering.workinstruction.repository.WorkInstructionStepRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Media upload/download orchestration: content-type + size validation, binary storage in MinIO,
 * metadata persistence, caption/order edits, and refcount-guarded deletion. All mutations are
 * restricted to DRAFT revisions. Copy-on-revision duplicates rows pointing at the same object key.
 */
@Service
@Transactional
public class MediaService {

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/png", "image/jpeg", "application/pdf", "video/mp4");
    private static final String VIDEO_MP4 = "video/mp4";

    private final MediaAttachmentRepository mediaRepository;
    private final WorkInstructionStepRepository stepRepository;
    private final MediaStorageService storage;
    private final MediaProperties properties;

    public MediaService(MediaAttachmentRepository mediaRepository,
                        WorkInstructionStepRepository stepRepository,
                        MediaStorageService storage,
                        MediaProperties properties) {
        this.mediaRepository = mediaRepository;
        this.stepRepository = stepRepository;
        this.storage = storage;
        this.properties = properties;
    }

    public MediaAttachmentDto upload(WorkInstructionRevision draft, UUID stepId, MultipartFile file) {
        requireDraft(draft);
        WorkInstructionStep step = requireStep(draft.getId(), stepId);

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new WorkInstructionValidationException(
                    "Unsupported media type: " + contentType,
                    List.of("allowed: image/png, image/jpeg, application/pdf, video/mp4"));
        }
        long limit = VIDEO_MP4.equals(contentType) ? properties.getMaxVideoBytes()
                : properties.getMaxImageBytes();
        if (file.getSize() > limit) {
            throw new WorkInstructionValidationException(
                    "Media exceeds the size limit",
                    List.of("max " + limit + " bytes for " + contentType));
        }

        UUID attachmentId = UUID.randomUUID();
        UUID orgId = draft.getWorkInstruction().getOrgId();
        UUID instructionId = draft.getWorkInstruction().getId();
        String objectKey = storage.objectKey(orgId, instructionId, attachmentId, file.getOriginalFilename());
        try {
            storage.put(objectKey, file.getInputStream(), file.getSize(), contentType);
        } catch (IOException ex) {
            throw new MediaStorageException("Could not read uploaded file stream", ex);
        }

        MediaAttachment attachment = new MediaAttachment();
        attachment.setStep(step);
        attachment.setFileName(file.getOriginalFilename());
        attachment.setContentType(contentType);
        attachment.setSizeBytes(file.getSize());
        attachment.setDisplayOrder(nextDisplayOrder(step.getId()));
        attachment.setStoragePath(objectKey);
        return toDto(mediaRepository.save(attachment));
    }

    @Transactional(readOnly = true)
    public List<MediaAttachmentDto> listForStep(UUID stepId) {
        return mediaRepository.findByStepIdOrderByDisplayOrderAsc(stepId).stream()
                .map(MediaService::toDto)
                .toList();
    }

    public MediaAttachmentDto patch(WorkInstructionRevision draft, UUID stepId, UUID attachmentId,
                                    String caption, Integer displayOrder) {
        requireDraft(draft);
        MediaAttachment attachment = requireAttachment(stepId, attachmentId);
        if (caption != null) {
            attachment.setCaption(caption);
        }
        if (displayOrder != null) {
            attachment.setDisplayOrder(displayOrder);
        }
        return toDto(mediaRepository.save(attachment));
    }

    public void delete(WorkInstructionRevision draft, UUID stepId, UUID attachmentId) {
        requireDraft(draft);
        MediaAttachment attachment = requireAttachment(stepId, attachmentId);
        String objectKey = attachment.getStoragePath();
        mediaRepository.delete(attachment);
        mediaRepository.flush();
        // Garbage-collect the binary only when no other revision's row still references it.
        if (mediaRepository.countByStoragePath(objectKey) == 0) {
            storage.delete(objectKey);
        }
    }

    /** Resolve download metadata, enforcing org ownership; binary streamed by the caller afterwards. */
    @Transactional(readOnly = true)
    public MediaDownload resolveDownload(UUID orgId, UUID attachmentId) {
        MediaAttachment attachment = mediaRepository.findById(attachmentId)
                .filter(a -> orgId.equals(a.getStep().getRevision().getWorkInstruction().getOrgId()))
                .orElseThrow(() -> new WorkInstructionNotFoundException(
                        "Media attachment not found: " + attachmentId));
        return new MediaDownload(attachment.getFileName(), attachment.getContentType(),
                attachment.getSizeBytes(), attachment.getStoragePath());
    }

    public InputStreamSupplier open(String objectKey) {
        return () -> storage.get(objectKey);
    }

    /** Copy media rows from a source step to a target step, sharing the same object keys. */
    public void copyMediaForStep(UUID sourceStepId, WorkInstructionStep targetStep) {
        for (MediaAttachment src : mediaRepository.findByStepIdOrderByDisplayOrderAsc(sourceStepId)) {
            MediaAttachment copy = new MediaAttachment();
            copy.setStep(targetStep);
            copy.setFileName(src.getFileName());
            copy.setContentType(src.getContentType());
            copy.setSizeBytes(src.getSizeBytes());
            copy.setCaption(src.getCaption());
            copy.setDisplayOrder(src.getDisplayOrder());
            copy.setStoragePath(src.getStoragePath());
            mediaRepository.save(copy);
        }
    }

    /** Functional supplier so the controller can open the stream lazily inside StreamingResponseBody. */
    @FunctionalInterface
    public interface InputStreamSupplier {
        java.io.InputStream get();
    }

    private int nextDisplayOrder(UUID stepId) {
        return mediaRepository.findByStepIdOrderByDisplayOrderAsc(stepId).stream()
                .mapToInt(MediaAttachment::getDisplayOrder)
                .max()
                .orElse(-1) + 1;
    }

    private WorkInstructionStep requireStep(UUID revisionId, UUID stepId) {
        return stepRepository.findByRevisionIdAndId(revisionId, stepId)
                .orElseThrow(() -> new WorkInstructionNotFoundException(
                        "Step not found in this revision: " + stepId));
    }

    private MediaAttachment requireAttachment(UUID stepId, UUID attachmentId) {
        return mediaRepository.findByStepIdAndId(stepId, attachmentId)
                .orElseThrow(() -> new WorkInstructionNotFoundException(
                        "Media attachment not found: " + attachmentId));
    }

    private static void requireDraft(WorkInstructionRevision revision) {
        if (revision.getRevisionStatus() != RevisionStatus.DRAFT) {
            throw new WorkInstructionConflictException(
                    "Media can only be modified on a DRAFT revision");
        }
    }

    private static MediaAttachmentDto toDto(MediaAttachment a) {
        return new MediaAttachmentDto(a.getId(), a.getStep().getId(), a.getFileName(),
                a.getContentType(), a.getSizeBytes(), a.getCaption(), a.getDisplayOrder());
    }
}
