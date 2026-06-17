import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ApprovalHistoryDto, ApprovalRecordDto, ApproveRequest, CreateOperationRequest, CreateRouteRequest,
  CreateStepRequest, GroupDto, GroupInput, LabourPlanLineDto, MaterialConsumptionDto,
  MutuallyExclusiveSetDto, MutuallyExclusiveSetInput, OperationDto, OperationResourceDto, Page,
  PatchOperationContentRequest, PatchOperationRequest, PatchRouteRequest, PatchStepRequest,
  QualityVariableDto, RouteApprovalStatusDto, RouteDto, SkillRequirementDto, StepDto,
  StepFileReferenceDto, ToolingRequirementDto, WorkInstructionLinkDto,
} from '../models/routing.model';

@Injectable({ providedIn: 'root' })
export class RoutingApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/routes';

  // ── Routes ─────────────────────────────────────────────────────────────────

  list(opts: { page?: number; size?: number; search?: string } = {}): Observable<Page<RouteDto>> {
    let params = new HttpParams().set('page', opts.page ?? 0).set('size', opts.size ?? 20);
    if (opts.search) params = params.set('search', opts.search);
    return this.http.get<Page<RouteDto>>(this.base, { params });
  }

  get(id: string): Observable<RouteDto> {
    return this.http.get<RouteDto>(`${this.base}/${id}`);
  }

  create(req: CreateRouteRequest): Observable<RouteDto> {
    return this.http.post<RouteDto>(this.base, req);
  }

  patch(id: string, req: PatchRouteRequest): Observable<RouteDto> {
    return this.http.patch<RouteDto>(`${this.base}/${id}`, req);
  }

  cancelDraft(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}/draft`);
  }

  // ── Edit lock (FR-031/032/033) ─────────────────────────────────────────────

  acquireLock(id: string): Observable<RouteDto> {
    return this.http.post<RouteDto>(`${this.base}/${id}/lock`, {});
  }

  releaseLock(id: string): Observable<RouteDto> {
    return this.http.post<RouteDto>(`${this.base}/${id}/unlock`, {});
  }

  forceUnlock(id: string): Observable<RouteDto> {
    return this.http.post<RouteDto>(`${this.base}/${id}/force-unlock`, {});
  }

  // ── Operations ───────────────────────────────────────────────────────────────

  listOperations(routeId: string): Observable<OperationDto[]> {
    return this.http.get<OperationDto[]>(`${this.base}/${routeId}/operations`);
  }

  addOperation(routeId: string, req: CreateOperationRequest): Observable<OperationDto> {
    return this.http.post<OperationDto>(`${this.base}/${routeId}/operations`, req);
  }

  patchOperation(routeId: string, opId: string, req: PatchOperationRequest): Observable<OperationDto> {
    return this.http.patch<OperationDto>(`${this.base}/${routeId}/operations/${opId}`, req);
  }

  deleteOperation(routeId: string, opId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${routeId}/operations/${opId}`);
  }

  // ── Operation detail (resources / labour) ─────────────────────────────────────

  listResources(routeId: string, opId: string): Observable<OperationResourceDto[]> {
    return this.http.get<OperationResourceDto[]>(`${this.base}/${routeId}/operations/${opId}/resources`);
  }

  addResource(routeId: string, opId: string, req: OperationResourceDto): Observable<OperationResourceDto> {
    return this.http.post<OperationResourceDto>(`${this.base}/${routeId}/operations/${opId}/resources`, req);
  }

  deleteResource(routeId: string, opId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${routeId}/operations/${opId}/resources/${id}`);
  }

  listLabourPlan(routeId: string, opId: string): Observable<LabourPlanLineDto[]> {
    return this.http.get<LabourPlanLineDto[]>(`${this.base}/${routeId}/operations/${opId}/labour-plan`);
  }

  addLabourPlan(routeId: string, opId: string, req: LabourPlanLineDto): Observable<LabourPlanLineDto> {
    return this.http.post<LabourPlanLineDto>(`${this.base}/${routeId}/operations/${opId}/labour-plan`, req);
  }

  deleteLabourPlan(routeId: string, opId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${routeId}/operations/${opId}/labour-plan/${id}`);
  }

  private detailBase(routeId: string, opId: string, kind: string): string {
    return `${this.base}/${routeId}/operations/${opId}/${kind}`;
  }

  listMaterials(routeId: string, opId: string): Observable<MaterialConsumptionDto[]> {
    return this.http.get<MaterialConsumptionDto[]>(this.detailBase(routeId, opId, 'materials'));
  }

  addMaterial(routeId: string, opId: string, req: MaterialConsumptionDto): Observable<MaterialConsumptionDto> {
    return this.http.post<MaterialConsumptionDto>(this.detailBase(routeId, opId, 'materials'), req);
  }

  deleteMaterial(routeId: string, opId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.detailBase(routeId, opId, 'materials')}/${id}`);
  }

  listQualityVariables(routeId: string, opId: string): Observable<QualityVariableDto[]> {
    return this.http.get<QualityVariableDto[]>(this.detailBase(routeId, opId, 'quality-variables'));
  }

  addQualityVariable(routeId: string, opId: string, req: QualityVariableDto): Observable<QualityVariableDto> {
    return this.http.post<QualityVariableDto>(this.detailBase(routeId, opId, 'quality-variables'), req);
  }

  deleteQualityVariable(routeId: string, opId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.detailBase(routeId, opId, 'quality-variables')}/${id}`);
  }

  listTooling(routeId: string, opId: string): Observable<ToolingRequirementDto[]> {
    return this.http.get<ToolingRequirementDto[]>(this.detailBase(routeId, opId, 'tooling'));
  }

  addTooling(routeId: string, opId: string, req: ToolingRequirementDto): Observable<ToolingRequirementDto> {
    return this.http.post<ToolingRequirementDto>(this.detailBase(routeId, opId, 'tooling'), req);
  }

  deleteTooling(routeId: string, opId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.detailBase(routeId, opId, 'tooling')}/${id}`);
  }

  listSkills(routeId: string, opId: string): Observable<SkillRequirementDto[]> {
    return this.http.get<SkillRequirementDto[]>(this.detailBase(routeId, opId, 'skills'));
  }

  addSkill(routeId: string, opId: string, req: SkillRequirementDto): Observable<SkillRequirementDto> {
    return this.http.post<SkillRequirementDto>(this.detailBase(routeId, opId, 'skills'), req);
  }

  deleteSkill(routeId: string, opId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.detailBase(routeId, opId, 'skills')}/${id}`);
  }

  listWorkInstructions(routeId: string, opId: string): Observable<WorkInstructionLinkDto[]> {
    return this.http.get<WorkInstructionLinkDto[]>(this.detailBase(routeId, opId, 'work-instruction'));
  }

  addWorkInstruction(routeId: string, opId: string, req: WorkInstructionLinkDto):
      Observable<WorkInstructionLinkDto> {
    return this.http.post<WorkInstructionLinkDto>(this.detailBase(routeId, opId, 'work-instruction'), req);
  }

  deleteWorkInstruction(routeId: string, opId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.detailBase(routeId, opId, 'work-instruction')}/${id}`);
  }

  listStepFiles(routeId: string, opId: string): Observable<StepFileReferenceDto[]> {
    return this.http.get<StepFileReferenceDto[]>(this.detailBase(routeId, opId, 'step-file'));
  }

  addStepFile(routeId: string, opId: string, req: StepFileReferenceDto): Observable<StepFileReferenceDto> {
    return this.http.post<StepFileReferenceDto>(this.detailBase(routeId, opId, 'step-file'), req);
  }

  deleteStepFile(routeId: string, opId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.detailBase(routeId, opId, 'step-file')}/${id}`);
  }

  // ── Steps ──────────────────────────────────────────────────────────────────────

  listSteps(routeId: string, opId: string): Observable<StepDto[]> {
    return this.http.get<StepDto[]>(`${this.base}/${routeId}/operations/${opId}/steps`);
  }

  addStep(routeId: string, opId: string, req: CreateStepRequest): Observable<StepDto> {
    return this.http.post<StepDto>(`${this.base}/${routeId}/operations/${opId}/steps`, req);
  }

  patchStep(routeId: string, opId: string, stepId: string, req: PatchStepRequest): Observable<StepDto> {
    return this.http.patch<StepDto>(`${this.base}/${routeId}/operations/${opId}/steps/${stepId}`, req);
  }

  deleteStep(routeId: string, opId: string, stepId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${routeId}/operations/${opId}/steps/${stepId}`);
  }

  // ── Groups + mutually-exclusive sets ─────────────────────────────────────────────

  listGroups(routeId: string): Observable<GroupDto[]> {
    return this.http.get<GroupDto[]>(`${this.base}/${routeId}/groups`);
  }

  putGroups(routeId: string, groups: GroupInput[]): Observable<GroupDto[]> {
    return this.http.put<GroupDto[]>(`${this.base}/${routeId}/groups`, { groups });
  }

  listMutuallyExclusiveSets(routeId: string): Observable<MutuallyExclusiveSetDto[]> {
    return this.http.get<MutuallyExclusiveSetDto[]>(`${this.base}/${routeId}/mutually-exclusive-sets`);
  }

  putMutuallyExclusiveSets(routeId: string, sets: MutuallyExclusiveSetInput[]):
      Observable<MutuallyExclusiveSetDto[]> {
    return this.http.put<MutuallyExclusiveSetDto[]>(
      `${this.base}/${routeId}/mutually-exclusive-sets`, { sets });
  }

  // ── Approval + revisioning (US7/US8) ─────────────────────────────────────────────

  submit(routeId: string): Observable<RouteApprovalStatusDto> {
    return this.http.post<RouteApprovalStatusDto>(`${this.base}/${routeId}/submit`, {});
  }

  approve(routeId: string, req: ApproveRequest): Observable<RouteApprovalStatusDto> {
    return this.http.post<RouteApprovalStatusDto>(`${this.base}/${routeId}/approve`, req);
  }

  reject(routeId: string, req: { password: string; reason?: string }): Observable<RouteApprovalStatusDto> {
    return this.http.post<RouteApprovalStatusDto>(`${this.base}/${routeId}/reject`, req);
  }

  approvalStatus(routeId: string): Observable<RouteApprovalStatusDto> {
    return this.http.get<RouteApprovalStatusDto>(`${this.base}/${routeId}/approval-status`);
  }

  approvalHistory(routeId: string): Observable<ApprovalHistoryDto> {
    return this.http.get<ApprovalHistoryDto>(`${this.base}/${routeId}/approval-history`);
  }

  startRouteRevision(routeId: string, reasonForRevision?: string): Observable<RouteDto> {
    return this.http.post<RouteDto>(`${this.base}/${routeId}/revisions`, { reasonForRevision });
  }

  startOperationRevision(routeId: string, opId: string): Observable<OperationDto> {
    return this.http.post<OperationDto>(`${this.base}/${routeId}/operations/${opId}/revision`, {});
  }

  patchOperationContent(routeId: string, opId: string, req: PatchOperationContentRequest):
      Observable<OperationDto> {
    return this.http.patch<OperationDto>(`${this.base}/${routeId}/operations/${opId}/content`, req);
  }

  submitOperation(routeId: string, opId: string): Observable<OperationDto> {
    return this.http.post<OperationDto>(`${this.base}/${routeId}/operations/${opId}/submit`, {});
  }

  approveOperation(routeId: string, opId: string, req: ApproveRequest): Observable<ApprovalRecordDto> {
    return this.http.post<ApprovalRecordDto>(`${this.base}/${routeId}/operations/${opId}/approve`, req);
  }
}
