export type Classification = 'ELECTRONIC' | 'MECHANICAL' | 'RAW_MATERIAL' | 'CONSUMABLE' | 'TOOLING' | 'SOFTWARE' | 'DOCUMENT';
export type MakeBuyCode = 'MAKE' | 'BUY' | 'EITHER';
export type ItemStatus = 'ACTIVE' | 'INACTIVE' | 'OBSOLETE';
export type CounterfeitRiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface ItemMasterDto {
  id: string;
  orgId: string;
  partNumber: string;
  revision: string;
  description: string;
  unitOfMeasure: string;
  cageCode?: string;
  classification: Classification;
  makeBuyCode: MakeBuyCode;
  status: ItemStatus;
  shelfLifeControlled: boolean;
  shelfLifeDays?: number;
  counterfeitRiskLevel?: CounterfeitRiskLevel;
  verificationRequired: boolean;
  approvedSuppliers?: string[];
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
  status?: ItemStatus;
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
  traceabilityMethod: string;
}

export interface PatchItemMasterRequest {
  description?: string;
  unitOfMeasure?: string;
  cageCode?: string;
  classification?: Classification;
  makeBuyCode?: MakeBuyCode;
  status?: ItemStatus;
  shelfLifeControlled?: boolean;
  shelfLifeDays?: number;
  counterfeitRiskLevel?: CounterfeitRiskLevel;
  verificationRequired?: boolean;
  approvedSuppliers?: string[];
}
