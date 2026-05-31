export type EcoStatus = 'DRAFT' | 'APPROVED' | 'IMPLEMENTED';

export interface EcoDto {
  id: string;
  orgId: string;
  ecoNumber: string;
  title: string;
  description?: string;
  status: EcoStatus;
  initiatedBy?: string;
  approvedBy?: string;
  approvedAt?: string;
  affectedItemIds: string[];
  outputBomIds: string[];
  concurrentEcoWarning: boolean;
  createdBy: string;
  createdAt: string;
}

export interface CreateEcoRequest {
  title: string;
  description?: string;
  affectedItemIds: string[];
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
