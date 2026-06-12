import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import {
  BomDto,
  BomLineDto,
  BomSummaryDto,
  BomRevisionSummaryDto,
  BomExplosionNode,
  CreateBomRequest,
  CreateBomLineRequest,
  PatchBomHeaderRequest,
  Page,
  RevisionStatus,
} from '../models/bom.model';

@Injectable({ providedIn: 'root' })
export class BomApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/boms';

  listForItem(parentItemId: string): Observable<BomSummaryDto[]> {
    return this.listHeaders(undefined, 0, 200).pipe(
      map(page => page.content.filter(b => b.parentItemId === parentItemId)),
    );
  }

  listHeaders(search?: string, page = 0, size = 20): Observable<Page<BomSummaryDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (search) { params = params.set('search', search); }
    return this.http.get<Page<BomSummaryDto>>(`${this.base}/headers`, { params });
  }

  getById(id: string, revisionStatus?: RevisionStatus, revisionNumber?: number): Observable<BomDto> {
    let params = new HttpParams();
    if (revisionStatus) params = params.set('revisionStatus', revisionStatus);
    if (revisionNumber !== undefined) params = params.set('revisionNumber', revisionNumber);
    return this.http.get<BomDto>(`${this.base}/${id}`, { params: params.keys().length ? params : undefined });
  }

  create(req: CreateBomRequest): Observable<BomDto> {
    return this.http.post<BomDto>(this.base, req);
  }

  listRevisions(bomId: string): Observable<BomRevisionSummaryDto[]> {
    return this.http.get<BomRevisionSummaryDto[]>(`${this.base}/${bomId}/revisions`);
  }

  getLines(bomId: string): Observable<BomLineDto[]> {
    return this.http.get<BomLineDto[]>(`${this.base}/${bomId}/lines`);
  }

  addLine(bomId: string, req: CreateBomLineRequest): Observable<BomLineDto> {
    return this.http.post<BomLineDto>(`${this.base}/${bomId}/lines`, req);
  }

  patchLine(bomId: string, lineId: string, req: { quantity?: number; referenceDesignators?: string }): Observable<BomLineDto> {
    return this.http.patch<BomLineDto>(`${this.base}/${bomId}/lines/${lineId}`, req);
  }

  removeLine(bomId: string, lineId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${bomId}/lines/${lineId}`);
  }

  submitBom(bomId: string): Observable<BomDto> {
    return this.http.post<BomDto>(`${this.base}/${bomId}/submit`, {});
  }

  approveBom(bomId: string): Observable<BomDto> {
    return this.http.post<BomDto>(`${this.base}/${bomId}/approve`, {});
  }

  rejectBom(bomId: string, rejectionReason: string): Observable<BomDto> {
    return this.http.post<BomDto>(`${this.base}/${bomId}/reject`, { rejectionReason });
  }

  cancelBomDraft(bomId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${bomId}/draft`);
  }

  explode(
    bomId: string,
    format: 'flat' | 'indented' = 'indented',
    asOfDate?: string,
    asOfUnit?: string,
    maxDepth?: number,
  ): Observable<BomExplosionNode[]> {
    let params = new HttpParams().set('format', format);
    if (asOfDate) params = params.set('asOfDate', asOfDate);
    if (asOfUnit) params = params.set('asOfUnit', asOfUnit);
    if (maxDepth) params = params.set('maxDepth', maxDepth);
    return this.http.get<BomExplosionNode[]>(`${this.base}/${bomId}/explosion`, { params });
  }

  patchHeader(bomId: string, req: PatchBomHeaderRequest): Observable<BomDto> {
    return this.http.patch<BomDto>(`${this.base}/${bomId}`, req);
  }

  downloadExplosion(
    bomId: string,
    format: 'flat' | 'indented',
    fileType: 'csv' | 'pdf',
    asOfDate?: string,
    maxDepth?: number,
  ): Observable<Blob> {
    let params = new HttpParams().set('format', format).set('download', fileType);
    if (asOfDate) { params = params.set('asOfDate', asOfDate); }
    if (maxDepth) { params = params.set('maxDepth', maxDepth); }
    return this.http.get(`${this.base}/${bomId}/explosion/download`, {
      params,
      responseType: 'blob',
    });
  }
}
