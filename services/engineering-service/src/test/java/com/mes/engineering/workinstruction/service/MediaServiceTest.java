package com.mes.engineering.workinstruction.service;

import com.mes.engineering.workinstruction.domain.MediaAttachment;
import com.mes.engineering.workinstruction.domain.RevisionStatus;
import com.mes.engineering.workinstruction.domain.WorkInstruction;
import com.mes.engineering.workinstruction.domain.WorkInstructionRevision;
import com.mes.engineering.workinstruction.domain.WorkInstructionStep;
import com.mes.engineering.workinstruction.repository.MediaAttachmentRepository;
import com.mes.engineering.workinstruction.repository.WorkInstructionStepRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaServiceTest {

    private final MediaAttachmentRepository mediaRepo = mock(MediaAttachmentRepository.class);
    private final WorkInstructionStepRepository stepRepo = mock(WorkInstructionStepRepository.class);
    private final MediaStorageService storage = mock(MediaStorageService.class);
    private final MediaProperties properties = new MediaProperties();
    private final MediaService service =
            new MediaService(mediaRepo, stepRepo, storage, properties);

    private static WorkInstructionRevision draft() {
        WorkInstruction wi = new WorkInstruction();
        wi.setOrgId(UUID.randomUUID());
        WorkInstructionRevision r = new WorkInstructionRevision();
        r.setRevisionStatus(RevisionStatus.DRAFT);
        r.setWorkInstruction(wi);
        return r;
    }

    @Test
    void rejectsUnsupportedContentType() {
        WorkInstructionRevision draft = draft();
        when(stepRepo.findByRevisionIdAndId(any(), any()))
                .thenReturn(Optional.of(new WorkInstructionStep()));
        MockMultipartFile bad = new MockMultipartFile(
                "file", "x.exe", "application/octet-stream", new byte[]{1, 2, 3});
        assertThatThrownBy(() -> service.upload(draft, UUID.randomUUID(), bad))
                .isInstanceOf(WorkInstructionValidationException.class);
        verify(storage, never()).put(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void rejectsOversizeImage() {
        properties.setMaxImageBytes(10);
        WorkInstructionRevision draft = draft();
        when(stepRepo.findByRevisionIdAndId(any(), any()))
                .thenReturn(Optional.of(new WorkInstructionStep()));
        MockMultipartFile big = new MockMultipartFile(
                "file", "big.png", "image/png", new byte[100]);
        assertThatThrownBy(() -> service.upload(draft, UUID.randomUUID(), big))
                .isInstanceOf(WorkInstructionValidationException.class);
        verify(storage, never()).put(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void deleteGarbageCollectsBinaryWhenNoOtherReferenceRemains() {
        WorkInstructionRevision draft = draft();
        MediaAttachment att = new MediaAttachment();
        att.setStoragePath("org/instr/att.png");
        when(mediaRepo.findByStepIdAndId(any(), any())).thenReturn(Optional.of(att));
        when(mediaRepo.countByStoragePath("org/instr/att.png")).thenReturn(0L);

        service.delete(draft, UUID.randomUUID(), UUID.randomUUID());

        verify(mediaRepo).delete(att);
        verify(storage).delete("org/instr/att.png");
    }

    @Test
    void deleteRetainsBinaryWhenAnotherRevisionStillReferencesIt() {
        WorkInstructionRevision draft = draft();
        MediaAttachment att = new MediaAttachment();
        att.setStoragePath("org/instr/shared.png");
        when(mediaRepo.findByStepIdAndId(any(), any())).thenReturn(Optional.of(att));
        when(mediaRepo.countByStoragePath("org/instr/shared.png")).thenReturn(1L);

        service.delete(draft, UUID.randomUUID(), UUID.randomUUID());

        verify(mediaRepo).delete(att);
        verify(storage, never()).delete(anyString());
    }
}
