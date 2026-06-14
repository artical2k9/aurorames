package com.mes.engineering.workinstruction.repository;

import com.mes.engineering.workinstruction.domain.MediaAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaAttachmentRepository extends JpaRepository<MediaAttachment, UUID> {

    List<MediaAttachment> findByStepIdOrderByDisplayOrderAsc(UUID stepId);

    Optional<MediaAttachment> findByStepIdAndId(UUID stepId, UUID id);

    /** Reference count for a shared object key — guards binary deletion across revisions. */
    long countByStoragePath(String storagePath);
}
