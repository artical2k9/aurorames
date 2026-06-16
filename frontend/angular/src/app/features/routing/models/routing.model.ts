export interface Page<T> {
  content: T[];
  totalElements: number;
}

export type RouteStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED';
export type OperationStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED';
export type DerivedType = 'NORMAL' | 'PARALLEL';
export type LabourActivityType = 'SETUP' | 'RUN' | 'INSPECTION' | 'TRANSPORT';
export type Basis = 'PER_ITEM' | 'PER_LOT';
export type MutuallyExclusiveLevel = 'OPERATION' | 'GROUP' | 'STEP';

// ── Routes ───────────────────────────────────────────────────────────────────

export interface RouteDto {
  id: string;
  partId: string;
  partRevision: string;
  routeTypeId: string;
  bomId?: string;
  bomRevisionId?: string;
  inspectionPlanRevisionId?: string;
  revision: number;
  status: RouteStatus;
  reasonForRevision: string;
  bomRevisionSuperseded: boolean;
  inspectionPlanRevisionSuperseded: boolean;
  customFields?: Record<string, unknown>;
}

export interface CreateRouteRequest {
  partId: string;
  partRevision: string;
  routeTypeId: string;
  bomId?: string;
  bomRevisionId?: string;
  inspectionPlanRevisionId?: string;
  reasonForRevision: string;
  customFields?: Record<string, unknown>;
}

export interface PatchRouteRequest {
  reasonForRevision?: string;
  bomId?: string;
  bomRevisionId?: string;
  inspectionPlanRevisionId?: string;
  customFields?: Record<string, unknown>;
}

// ── Operations / groups / steps ──────────────────────────────────────────────

export interface OperationDto {
  id: string;
  operationNumber: number;
  sequenceNumber: number;
  description?: string;
  derivedType: DerivedType;
  optional: boolean;
  osp: boolean;
  significantProcessTypeId?: string;
  supplierId?: string;
  groupId?: string;
  clocking: boolean;
  operationRevision: number;
  operationStatus: OperationStatus;
}

export interface CreateOperationRequest {
  operationNumber: number;
  sequenceNumber: number;
  description?: string;
  optional?: boolean;
  osp?: boolean;
  significantProcessTypeId?: string;
  supplierId?: string;
  clocking?: boolean;
}

export type PatchOperationRequest = Partial<CreateOperationRequest>;

export interface GroupDto {
  id: string;
  name?: string;
  groupSequenceNumber: number;
  optional: boolean;
  derivedType: DerivedType;
  operationIds: string[];
}

export interface GroupInput {
  name?: string;
  groupSequenceNumber: number;
  optional: boolean;
  operationIds: string[];
}

export interface StepDto {
  id: string;
  stepNumber: number;
  stepSequenceNumber: number;
  description?: string;
  optional: boolean;
  derivedType: DerivedType;
}

export interface CreateStepRequest {
  stepNumber: number;
  stepSequenceNumber: number;
  description?: string;
  optional?: boolean;
}

export type PatchStepRequest = Partial<CreateStepRequest>;

export interface MutuallyExclusiveSetDto {
  id: string;
  level: MutuallyExclusiveLevel;
  sequenceNumber: number;
  memberIds: string[];
}

export interface MutuallyExclusiveSetInput {
  level: MutuallyExclusiveLevel;
  memberIds: string[];
}

// ── Operation detail (resources / labour standards) ──────────────────────────

export interface OperationResourceDto {
  id?: string;
  workCentreId: string;
}

export interface LabourPlanLineDto {
  id?: string;
  labourActivityType: LabourActivityType;
  labourPlanTypeId?: string;
  labourCodeId?: string;
  timeValue: number;
  basis: Basis;
}

// ── Approval (US7/US8) ───────────────────────────────────────────────────────

export interface ApproveRequest {
  password: string;
  meaning: string;
  significantProcessTypeId?: string;
}

export interface RouteApprovalStatusDto {
  routeId: string;
  revision: number;
  status: RouteStatus;
  standardApprovalPresent: boolean;
  outstandingSignificantProcessTypeIds: string[];
}

export interface ApprovalRecordDto {
  id: string;
  subjectType: 'ROUTE_REVISION' | 'OPERATION_REVISION';
  subjectId: string;
  revision: number;
  actor: string;
  actorRole?: string;
  meaning: string;
  signedAt: string;
  significantProcessApprover: boolean;
  significantProcessTypeId?: string;
  governingRouteRevision?: number;
}

export interface RouteRevisionHistoryDto {
  routeRevision: number;
  routeApprovals: ApprovalRecordDto[];
  operationApprovals: ApprovalRecordDto[];
}

export interface ApprovalHistoryDto {
  routeId: string;
  revisions: RouteRevisionHistoryDto[];
}

// ── Reference data (Settings, US10) ──────────────────────────────────────────

export interface ReferenceDataDto {
  id?: string;
  code: string;
  name: string;
  active: boolean;
}

export type WorkCentreDto = ReferenceDataDto;
export type LabourCodeDto = ReferenceDataDto;
export type LabourPlanTypeDto = ReferenceDataDto & { seeded?: boolean };
export type SupplierDto = ReferenceDataDto;

export interface RouteTypeDto extends ReferenceDataDto {
  isStandard: boolean;
  seeded?: boolean;
}

export interface SignificantProcessTypeDto extends ReferenceDataDto {
  requiredApproverRole: string;
}
