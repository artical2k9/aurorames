import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CharacteristicDto, InspectionPlanDto, InspectionPlanSummaryDto, Page, RevisionSummaryDto,
} from '../models/inspection-plan.model';

@Injectable({ providedIn: 'root' })
export class InspectionPlanApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/inspection-plans';

  // ── Plans & revisions ────────────────────────────────────────────────────

  listPlans(opts: { page?: number; size?: number; search?: string } = {}):
      Observable<Page<InspectionPlanSummaryDto>> {
    let params = new HttpParams()
      .set('page', opts.page ?? 0)
      .set('size', opts.size ?? 20);
    if (opts.search) params = params.set('search', opts.search);
    return this.http.get<Page<InspectionPlanSummaryDto>>(this.base, { params });
  }

  getPlan(id: string, opts: { revisionNumber?: number } = {}): Observable<InspectionPlanDto> {
    let params = new HttpParams();
    if (opts.revisionNumber !== undefined) {
      params = params.set('revisionNumber', opts.revisionNumber);
    }
    return this.http.get<InspectionPlanDto>(`${this.base}/${id}`, { params });
  }

  createPlan(req: { itemId: string; name: string; description?: string }):
      Observable<InspectionPlanDto> {
    return this.http.post<InspectionPlanDto>(this.base, req);
  }

  patchHeader(id: string, req: Partial<InspectionPlanDto>): Observable<InspectionPlanDto> {
    return this.http.patch<InspectionPlanDto>(`${this.base}/${id}`, req);
  }

  deletePlan(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  listRevisions(id: string): Observable<RevisionSummaryDto[]> {
    return this.http.get<RevisionSummaryDto[]>(`${this.base}/${id}/revisions`);
  }

  submit(id: string): Observable<InspectionPlanDto> {
    return this.http.post<InspectionPlanDto>(`${this.base}/${id}/submit`, {});
  }

  approve(id: string): Observable<InspectionPlanDto> {
    return this.http.post<InspectionPlanDto>(`${this.base}/${id}/approve`, {});
  }

  reject(id: string, reason: string): Observable<InspectionPlanDto> {
    return this.http.post<InspectionPlanDto>(`${this.base}/${id}/reject`, { reason });
  }

  createRevision(id: string): Observable<InspectionPlanDto> {
    return this.http.post<InspectionPlanDto>(`${this.base}/${id}/revisions`, {});
  }

  cancelDraft(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}/draft`);
  }

  // ── Characteristics ──────────────────────────────────────────────────────

  listCharacteristics(id: string, revisionNumber?: number): Observable<CharacteristicDto[]> {
    let params = new HttpParams();
    if (revisionNumber !== undefined) {
      params = params.set('revisionNumber', revisionNumber);
    }
    return this.http.get<CharacteristicDto[]>(`${this.base}/${id}/characteristics`, { params });
  }

  addCharacteristic(id: string, req: Partial<CharacteristicDto>): Observable<CharacteristicDto> {
    return this.http.post<CharacteristicDto>(`${this.base}/${id}/characteristics`, req);
  }

  updateCharacteristic(id: string, charId: string, req: Partial<CharacteristicDto>):
      Observable<CharacteristicDto> {
    return this.http.patch<CharacteristicDto>(`${this.base}/${id}/characteristics/${charId}`, req);
  }

  deleteCharacteristic(id: string, charId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}/characteristics/${charId}`);
  }
}
