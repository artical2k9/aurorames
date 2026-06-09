export type BomStatus = 'DRAFT' | 'RELEASED' | 'OBSOLETE';

export interface BomSummaryDto {
  bomId:           string;
  bomRevision:     string;
  bomStatus:       BomStatus;
  bomDescription:  string | null;
  parentItemId:    string;
  partNumber:      string;
  revision:        string;
  itemDescription: string;
  classification:  string;
  unitOfMeasure:   string | null;
  itemStatus:      string;
  createdBy:       string;
  createdAt:       string;
  customFields?:   Record<string, unknown>;
}
export type EffectivityMethod = 'NONE' | 'DATE' | 'UNIT';

export interface BomDto {
  id: string;
  orgId: string;
  parentItemId: string;
  bomRevision: string;
  status: BomStatus;
  description?: string;
  ecoId?: string;
  releasedBy?: string;
  releasedAt?: string;
  createdBy: string;
  createdAt: string;
  reasonForRevision?: string;
  productionLine?: string;
  bomType?: string;
  effectivityType?: string;
  customFields?: Record<string, unknown>;
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
  bomRevision: string;
  description?: string;
  ecoId?: string;
}

export interface CreateBomLineRequest {
  componentItemId: string;
  quantity: number;
  unitOfMeasure?: string;
  findNumber?: string;
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
