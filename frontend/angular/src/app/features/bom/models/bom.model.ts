export type RevisionStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED';

export interface BomSummaryDto {
  bomId:              string;
  bomRevisionId:      string;
  revision:           number;
  revisionStatus:     RevisionStatus;
  hasDraft:           boolean;
  hasPendingApproval: boolean;
  description:        string | null;
  parentItemId:       string;
  partNumber:         string;
  itemRevision:       number;
  itemRevisionStatus: string;
  createdBy:          string;
  createdAt:          string;
  customFields?:      Record<string, unknown>;
}

export type EffectivityMethod = 'NONE' | 'DATE' | 'UNIT';

export interface BomDto {
  id:               string;
  orgId:            string;
  parentItemId:     string;
  bomRevisionId:    string;
  revision:         number;
  revisionStatus:   RevisionStatus;
  hasDraft:         boolean;
  hasPendingApproval: boolean;
  description?:     string;
  ecoId?:           string;
  reasonForRevision?: string;
  productionLine?:  string;
  bomType?:         string;
  effectivityType?: string;
  customFields?:    Record<string, unknown>;
  submittedBy?:     string;
  submittedAt?:     string;
  approvedBy?:      string;
  approvedAt?:      string;
  rejectedBy?:      string;
  rejectedAt?:      string;
  rejectionReason?: string;
  createdBy:        string;
  createdAt:        string;
  modifiedBy?:      string;
  modifiedAt?:      string;
}

export interface BomLineDto {
  id: string;
  bomId: string;
  componentItemId: string;
  partNumber: string;
  revision: string;
  description: string;
  makeBuyCode?: string;
  quantity?: number;
  unitOfMeasure?: string;
  findNumber?: string;
  referenceDesignators?: string;
  effectivityMethod?: EffectivityMethod;
  effectiveFromDate?: string;
  effectiveToDate?: string;
  effectiveFromUnit?: string;
  effectiveToUnit?: string;
  counterfeitRiskAlert: boolean;
  componentObsoleted: boolean;
  createdBy: string;
  createdAt: string;
  customFields?: Record<string, unknown>;
}

export interface BomExplosionNode {
  componentItemId: string;
  parentItemId: string;
  depth: number;
  findNumber?: string;
  quantity?: number;
  makeBuyCode?: string;
  partNumber: string;
  revision: string;
  description: string;
  unitOfMeasure: string;
  counterfeitRiskAlert: boolean;
  componentObsoleted: boolean;
  children?: BomExplosionNode[];
}

export interface CreateBomRequest {
  parentItemId: string;
  description?: string;
  ecoId?: string;
}

export interface CreateBomLineRequest {
  componentItemRevisionId: string;
  quantity: number;
  unitOfMeasure: string;
  findNumber: string;
  referenceDesignators?: string;
  effectivityMethod?: EffectivityMethod;
  effectiveFromDate?: string;
  effectiveToDate?: string;
  effectiveFromUnit?: string;
  effectiveToUnit?: string;
}

export interface PatchBomHeaderRequest {
  description?: string;
  reasonForRevision?: string;
  productionLine?: string;
  bomType?: string;
  effectivityType?: string;
  customFields?: Record<string, unknown>;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
