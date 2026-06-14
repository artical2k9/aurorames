import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ItemMasterDto,
  ItemMasterListParams,
  ItemRevisionSummaryDto,
  Page,
  CreateItemMasterRequest,
  PatchItemMasterRequest,
  RevisionStatus,
} from '../models/item-master.model';

@Injectable({ providedIn: 'root' })
export class ItemMasterApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/item-master';

  list(params: ItemMasterListParams): Observable<Page<ItemMasterDto>> {
    let httpParams = new HttpParams()
      .set('page', params.page ?? 0)
      .set('size', params.size ?? 20);
    if (params.search)               httpParams = httpParams.set('search', params.search);
    if (params.classification)       httpParams = httpParams.set('classification', params.classification);
    if (params.revisionStatus)       httpParams = httpParams.set('revisionStatus', params.revisionStatus);
    if (params.makeBuyCode)          httpParams = httpParams.set('makeBuyCode', params.makeBuyCode);
    if (params.counterfeitRiskLevel) httpParams = httpParams.set('counterfeitRiskLevel', params.counterfeitRiskLevel);
    return this.http.get<Page<ItemMasterDto>>(this.base, { params: httpParams });
  }

  getById(id: string, revisionStatus?: RevisionStatus, revisionNumber?: number): Observable<ItemMasterDto> {
    let params = new HttpParams();
    if (revisionStatus) params = params.set('revisionStatus', revisionStatus);
    if (revisionNumber !== undefined) params = params.set('revisionNumber', revisionNumber);
    return this.http.get<ItemMasterDto>(`${this.base}/${id}`, { params: params.keys().length ? params : undefined });
  }

  listRevisions(id: string): Observable<ItemRevisionSummaryDto[]> {
    return this.http.get<ItemRevisionSummaryDto[]>(`${this.base}/${id}/revisions`);
  }

  create(req: CreateItemMasterRequest): Observable<ItemMasterDto> {
    return this.http.post<ItemMasterDto>(this.base, req);
  }

  patch(id: string, req: PatchItemMasterRequest): Observable<ItemMasterDto> {
    return this.http.patch<ItemMasterDto>(`${this.base}/${id}`, req);
  }

  submit(id: string): Observable<ItemMasterDto> {
    return this.http.post<ItemMasterDto>(`${this.base}/${id}/submit`, {});
  }

  approve(id: string): Observable<ItemMasterDto> {
    return this.http.post<ItemMasterDto>(`${this.base}/${id}/approve`, {});
  }

  reject(id: string, rejectionReason: string): Observable<ItemMasterDto> {
    return this.http.post<ItemMasterDto>(`${this.base}/${id}/reject`, { rejectionReason });
  }

  cancelDraft(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}/draft`);
  }

  clone(id: string): Observable<ItemMasterDto> {
    return this.http.post<ItemMasterDto>(`${this.base}/${id}/clone`, {});
  }
}
