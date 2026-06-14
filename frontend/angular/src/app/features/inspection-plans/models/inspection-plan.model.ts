export interface Page<T> {
  content: T[];
  totalElements: number;
}

export type RevisionStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED';
export type CharacteristicType = 'SPECIFIC' | 'COMMON' | 'CALCULATED';
export type CharacteristicSource = 'DESIGN' | 'IN_PROCESS';
export type SampleSizeRule = 'ALL' | 'FIXED_COUNT';
export type RecordingBasis = 'PER_PIECE' | 'PER_LOT';

export interface InspectionPlanSummaryDto {
  id: string;
  itemId: string;
  partNumber: string;
  revisionId: string;
  revision: number;
  revisionStatus: RevisionStatus;
  name: string;
  hasDraft: boolean;
  customFields?: Record<string, unknown>;
  createdBy?: string;
  createdAt?: string;
}

export interface InspectionPlanDto {
  id: string;
  orgId: string;
  itemId: string;
  partNumber: string;
  revisionId: string;
  revision: number;
  revisionStatus: RevisionStatus;
  name: string;
  description?: string;
  reasonForRevision?: string;
  customFields?: Record<string, unknown>;
  hasDraft: boolean;
  hasPendingApproval: boolean;
  submittedBy?: string;
  submittedAt?: string;
  approvedBy?: string;
  approvedAt?: string;
  rejectedBy?: string;
  rejectedAt?: string;
  rejectionReason?: string;
  createdBy?: string;
  createdAt?: string;
  modifiedBy?: string;
  modifiedAt?: string;
}

export interface RevisionSummaryDto {
  revisionId: string;
  revision: number;
  revisionStatus: RevisionStatus;
  name: string;
  reasonForRevision?: string;
  submittedBy?: string;
  submittedAt?: string;
  approvedBy?: string;
  approvedAt?: string;
  createdBy?: string;
  createdAt?: string;
}

export interface CharacteristicDto {
  id: string;
  characteristicNumber: number;
  name: string;
  description?: string;
  source: CharacteristicSource;
  characteristicType: CharacteristicType;
  inspectionMethod?: string;
  gaugeType?: string;
  unitOfMeasure?: string;
  sampleSizeRule: SampleSizeRule;
  sampleSizeCount?: number;
  recordingBasis: RecordingBasis;
  nominalValue?: number;
  lowerLimit?: number;
  upperLimit?: number;
  expectedBoolean?: boolean;
  expression?: string;
  customFields?: Record<string, unknown>;
}
