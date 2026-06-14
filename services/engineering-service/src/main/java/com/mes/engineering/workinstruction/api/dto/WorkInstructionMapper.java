package com.mes.engineering.workinstruction.api.dto;

import com.mes.engineering.workinstruction.domain.ElectronicSignature;
import com.mes.engineering.workinstruction.domain.WorkInstruction;
import com.mes.engineering.workinstruction.domain.WorkInstructionRevision;
import com.mes.engineering.workinstruction.domain.WorkInstructionStep;

import java.util.List;

public final class WorkInstructionMapper {

    private WorkInstructionMapper() {
    }

    public static WorkInstructionDto toDto(WorkInstruction wi, WorkInstructionRevision rev,
                                           boolean hasDraft, boolean hasPending,
                                           List<StepDto> steps, List<SignatureDto> signatures) {
        return new WorkInstructionDto(
                wi.getId(),
                wi.getIdentifier(),
                rev.getId(),
                rev.getRevision(),
                rev.getRevisionStatus().name(),
                rev.getTitle(),
                rev.getDescription(),
                rev.getPartContext(),
                rev.getReasonForRevision(),
                hasDraft,
                hasPending,
                rev.getCustomFields(),
                rev.getSubmittedBy(),
                rev.getApprovedBy(),
                rev.getRejectedBy(),
                rev.getRejectionReason(),
                steps,
                signatures);
    }

    public static WorkInstructionSummaryDto toSummaryDto(WorkInstruction wi,
                                                         WorkInstructionRevision rev,
                                                         boolean hasDraft) {
        return new WorkInstructionSummaryDto(
                wi.getId(),
                wi.getIdentifier(),
                rev.getId(),
                rev.getRevision(),
                rev.getRevisionStatus().name(),
                rev.getTitle(),
                hasDraft,
                rev.getCustomFields());
    }

    public static StepDto toStepDto(WorkInstructionStep step, List<MediaAttachmentDto> media) {
        return new StepDto(step.getId(), step.getStepNumber(), step.getTitle(),
                step.getBodyHtml(), step.getCustomFields(), media);
    }

    public static SignatureDto toSignatureDto(ElectronicSignature sig) {
        return new SignatureDto(sig.getId(), sig.getSignerUserId(), sig.getSignerFullName(),
                sig.getSignedAt(), sig.getMeaning());
    }

    public static WorkInstructionRevisionSummaryDto toRevisionSummaryDto(WorkInstructionRevision r) {
        return new WorkInstructionRevisionSummaryDto(
                r.getId(), r.getRevision(), r.getRevisionStatus().name(),
                r.getSubmittedBy(), r.getSubmittedAt(),
                r.getApprovedBy(), r.getApprovedAt(),
                r.getRejectedBy(), r.getRejectedAt(), r.getRejectionReason());
    }
}
