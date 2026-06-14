import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpEvent, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  MediaAttachmentDto, Page, QualificationResultDto, RevisionSummaryDto, SkillRequirementDto,
  StepDto, WorkInstructionDto, WorkInstructionSummaryDto,
} from '../models/work-instruction.model';

@Injectable({ providedIn: 'root' })
export class WorkInstructionApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/work-instructions';

  // ── Work instructions & revisions ──────────────────────────────────────────

  list(opts: { page?: number; size?: number; search?: string } = {}):
      Observable<Page<WorkInstructionSummaryDto>> {
    let params = new HttpParams()
      .set('page', opts.page ?? 0)
      .set('size', opts.size ?? 20);
    if (opts.search) params = params.set('search', opts.search);
    return this.http.get<Page<WorkInstructionSummaryDto>>(this.base, { params });
  }

  get(id: string, opts: { revisionNumber?: number } = {}): Observable<WorkInstructionDto> {
    let params = new HttpParams();
    if (opts.revisionNumber !== undefined) {
      params = params.set('revisionNumber', opts.revisionNumber);
    }
    return this.http.get<WorkInstructionDto>(`${this.base}/${id}`, { params });
  }

  suggestIdentifier(prefix?: string): Observable<string> {
    let params = new HttpParams();
    if (prefix) params = params.set('prefix', prefix);
    return this.http.get(`${this.base}/identifier-suggestion`, { params, responseType: 'text' });
  }

  create(req: { identifier: string; title: string; description?: string; partContext?: string }):
      Observable<WorkInstructionDto> {
    return this.http.post<WorkInstructionDto>(this.base, req);
  }

  patchHeader(id: string, req: Partial<WorkInstructionDto>): Observable<WorkInstructionDto> {
    return this.http.patch<WorkInstructionDto>(`${this.base}/${id}`, req);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  listRevisions(id: string): Observable<RevisionSummaryDto[]> {
    return this.http.get<RevisionSummaryDto[]>(`${this.base}/${id}/revisions`);
  }

  submit(id: string): Observable<WorkInstructionDto> {
    return this.http.post<WorkInstructionDto>(`${this.base}/${id}/submit`, {});
  }

  approve(id: string, password: string): Observable<WorkInstructionDto> {
    return this.http.post<WorkInstructionDto>(`${this.base}/${id}/approve`, { password });
  }

  reject(id: string, reason: string): Observable<WorkInstructionDto> {
    return this.http.post<WorkInstructionDto>(`${this.base}/${id}/reject`, { reason });
  }

  createRevision(id: string): Observable<WorkInstructionDto> {
    return this.http.post<WorkInstructionDto>(`${this.base}/${id}/revisions`, {});
  }

  cancelDraft(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/cancel-draft`, {});
  }

  // ── Steps ──────────────────────────────────────────────────────────────────

  addStep(id: string, req: { title: string; bodyHtml?: string }): Observable<StepDto> {
    return this.http.post<StepDto>(`${this.base}/${id}/steps`, req);
  }

  updateStep(id: string, stepId: string, req: { title: string; bodyHtml?: string }):
      Observable<StepDto> {
    return this.http.patch<StepDto>(`${this.base}/${id}/steps/${stepId}`, req);
  }

  deleteStep(id: string, stepId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}/steps/${stepId}`);
  }

  reorderSteps(id: string, orderedStepIds: string[]): Observable<StepDto[]> {
    return this.http.post<StepDto[]>(`${this.base}/${id}/steps/reorder`, { orderedStepIds });
  }

  // ── Media ──────────────────────────────────────────────────────────────────

  /** Upload with progress events (HttpEventType.UploadProgress / Response). */
  uploadMedia(id: string, stepId: string, file: File): Observable<HttpEvent<MediaAttachmentDto>> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<MediaAttachmentDto>(
      `${this.base}/${id}/steps/${stepId}/media`, form,
      { reportProgress: true, observe: 'events' });
  }

  /** Authenticated download — goes through HttpClient so the bearer token is attached. */
  downloadMedia(attachmentId: string): Observable<Blob> {
    return this.http.get(`${this.base}/media/${attachmentId}`, { responseType: 'blob' });
  }

  deleteMedia(id: string, stepId: string, attachmentId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}/steps/${stepId}/media/${attachmentId}`);
  }

  // ── Skill requirements & qualification ─────────────────────────────────────

  listSkillRequirements(id: string, revisionNumber?: number): Observable<SkillRequirementDto[]> {
    let params = new HttpParams();
    if (revisionNumber !== undefined) params = params.set('revisionNumber', revisionNumber);
    return this.http.get<SkillRequirementDto[]>(`${this.base}/${id}/skill-requirements`, { params });
  }

  addSkillRequirement(id: string, skillId: string): Observable<SkillRequirementDto> {
    return this.http.post<SkillRequirementDto>(`${this.base}/${id}/skill-requirements`, { skillId });
  }

  removeSkillRequirement(id: string, reqId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}/skill-requirements/${reqId}`);
  }

  evaluateQualification(id: string, opts: { employeeId?: string; iamUserId?: string },
                        revisionNumber?: number): Observable<QualificationResultDto> {
    let params = new HttpParams();
    if (opts.employeeId) params = params.set('employeeId', opts.employeeId);
    if (opts.iamUserId) params = params.set('iamUserId', opts.iamUserId);
    if (revisionNumber !== undefined) params = params.set('revisionNumber', revisionNumber);
    return this.http.get<QualificationResultDto>(`${this.base}/${id}/qualification`, { params });
  }
}
