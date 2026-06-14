export interface Page<T> {
  content: T[];
  totalElements: number;
}

export type RevisionStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED';

export interface WorkInstructionSummaryDto {
  id: string;
  identifier: string;
  revisionId: string;
  revision: number;
  revisionStatus: RevisionStatus;
  title: string;
  hasDraft: boolean;
  customFields?: Record<string, unknown>;
}

export interface MediaAttachmentDto {
  id: string;
  stepId: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  caption?: string;
  displayOrder: number;
}

export interface StepDto {
  id: string;
  stepNumber: number;
  title: string;
  bodyHtml?: string;
  customFields?: Record<string, unknown>;
  media: MediaAttachmentDto[];
}

export interface SignatureDto {
  id: string;
  signerUserId: string;
  signerFullName: string;
  signedAt: string;
  meaning: string;
}

export interface WorkInstructionDto {
  id: string;
  identifier: string;
  revisionId: string;
  revision: number;
  revisionStatus: RevisionStatus;
  title: string;
  description?: string;
  partContext?: string;
  reasonForRevision?: string;
  hasDraft: boolean;
  hasPending: boolean;
  customFields?: Record<string, unknown>;
  submittedBy?: string;
  approvedBy?: string;
  rejectedBy?: string;
  rejectionReason?: string;
  steps: StepDto[];
  signatures: SignatureDto[];
}

export interface RevisionSummaryDto {
  revisionId: string;
  revision: number;
  revisionStatus: RevisionStatus;
  submittedBy?: string;
  submittedAt?: string;
  approvedBy?: string;
  approvedAt?: string;
  rejectedBy?: string;
  rejectedAt?: string;
  rejectionReason?: string;
}

export interface SkillRequirementDto {
  id: string;
  skillId: string;
  skillCode: string;
  skillName: string;
}

export interface MissingSkill {
  skillId: string;
  skillCode: string;
  state: string;
}

export interface QualificationResultDto {
  qualified: boolean;
  missing: MissingSkill[];
  reason: string;
}
