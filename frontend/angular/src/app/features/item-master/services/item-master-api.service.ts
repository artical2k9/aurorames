import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ItemMasterDto,
  ItemMasterListParams,
  Page,
  CreateItemMasterRequest,
  PatchItemMasterRequest,
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

  getById(id: string): Observable<ItemMasterDto> {
    return this.http.get<ItemMasterDto>(`${this.base}/${id}`);
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

  cancelDraft(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}/draft`);
  }

  clone(id: string): Observable<ItemMasterDto> {
    return this.http.post<ItemMasterDto>(`${this.base}/${id}/clone`, {});
  }
}
