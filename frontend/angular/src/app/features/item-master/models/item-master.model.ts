export type Classification =
  | 'RAW_MATERIAL'
  | 'PURCHASED_PART'
  | 'FABRICATED'
  | 'ASSEMBLY'
  | 'COTS'
  | 'SERVICE';

export type MakeBuyCode = 'MAKE' | 'BUY' | 'EITHER';
export type TraceabilityMethod = 'SERIAL' | 'LOT' | 'HEAT_CODE' | 'DATE_CODE' | 'NONE';
export type CounterfeitRiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type RevisionStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED';

export interface ItemMasterDto {
  id: string;
  revisionId: string;
  orgId: string;
  partNumber: string;
  revision: number;
  revisionStatus: RevisionStatus;
  hasDraft: boolean;
  hasPendingApproval: boolean;
  description: string;
  unitOfMeasure: string;
  cageCode?: string;
  classification: Classification;
  makeBuyCode: MakeBuyCode;
  traceabilityMethod: TraceabilityMethod;
  shelfLifeControlled: boolean;
  shelfLifeDays?: number;
  stepPartRef?: string;
  counterfeitRiskLevel?: CounterfeitRiskLevel;
  verificationRequired: boolean;
  approvedSuppliers?: string[];
  customFields?: Record<string, unknown>;
  rejectedBy?: string;
  rejectedAt?: string;
  rejectionReason?: string;
  createdBy: string;
  createdAt: string;
  modifiedBy: string;
  modifiedAt: string;
}

export interface ItemMasterListParams {
  page?: number;
  size?: number;
  search?: string;
  classification?: Classification;
  revisionStatus?: RevisionStatus;
  makeBuyCode?: MakeBuyCode;
  counterfeitRiskLevel?: CounterfeitRiskLevel;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface CreateItemMasterRequest {
  partNumber: string;
  revision: string;
  description: string;
  unitOfMeasure: string;
  classification: Classification;
  makeBuyCode: MakeBuyCode;
  traceabilityMethod: TraceabilityMethod;
  cageCode?: string;
  shelfLifeControlled?: boolean;
  shelfLifeDays?: number;
  counterfeitRiskLevel?: CounterfeitRiskLevel;
  verificationRequired?: boolean;
  approvedSuppliers?: string[];
  stepPartRef?: string;
  customFields?: Record<string, unknown>;
}

export interface ItemRevisionSummaryDto {
  revisionId: string;
  revision: number;
  revisionStatus: RevisionStatus;
  description?: string;
  submittedBy?: string;
  submittedAt?: string;
  approvedBy?: string;
  approvedAt?: string;
  createdBy: string;
  createdAt: string;
}

export interface PatchItemMasterRequest {
  description?: string;
  unitOfMeasure?: string;
  cageCode?: string;
  classification?: Classification;
  makeBuyCode?: MakeBuyCode;
  traceabilityMethod?: TraceabilityMethod;
  shelfLifeControlled?: boolean;
  shelfLifeDays?: number | null;
  counterfeitRiskLevel?: CounterfeitRiskLevel | null;
  verificationRequired?: boolean;
  approvedSuppliers?: string[];
  stepPartRef?: string;
  customFields?: Record<string, unknown>;
}
