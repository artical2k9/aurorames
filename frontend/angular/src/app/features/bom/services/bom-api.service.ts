import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  BomDto,
  BomLineDto,
  BomExplosionNode,
  CreateBomRequest,
  CreateBomLineRequest,
  PatchBomHeaderRequest,
} from '../models/bom.model';

@Injectable({ providedIn: 'root' })
export class BomApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/boms';

  listForItem(parentItemId: string): Observable<BomDto[]> {
    const params = new HttpParams().set('parentItemId', parentItemId);
    return this.http.get<BomDto[]>(this.base, { params });
  }

  getById(id: string): Observable<BomDto> {
    return this.http.get<BomDto>(`${this.base}/${id}`);
  }

  create(req: CreateBomRequest): Observable<BomDto> {
    return this.http.post<BomDto>(this.base, req);
  }

  getLines(bomId: string): Observable<BomLineDto[]> {
    return this.http.get<BomLineDto[]>(`${this.base}/${bomId}/lines`);
  }

  addLine(bomId: string, req: CreateBomLineRequest): Observable<BomLineDto> {
    return this.http.post<BomLineDto>(`${this.base}/${bomId}/lines`, req);
  }

  removeLine(bomId: string, lineId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${bomId}/lines/${lineId}`);
  }

  release(bomId: string): Observable<BomDto> {
    return this.http.post<BomDto>(`${this.base}/${bomId}/release`, {});
  }

  explode(
    bomId: string,
    format: 'flat' | 'indented' = 'indented',
    asOfDate?: string,
    asOfUnit?: string,
  ): Observable<BomExplosionNode[]> {
    let params = new HttpParams().set('format', format);
    if (asOfDate) params = params.set('asOfDate', asOfDate);
    if (asOfUnit) params = params.set('asOfUnit', asOfUnit);
    return this.http.get<BomExplosionNode[]>(`${this.base}/${bomId}/explosion`, { params });
  }

  patchHeader(bomId: string, req: PatchBomHeaderRequest): Observable<BomDto> {
    return this.http.patch<BomDto>(`${this.base}/${bomId}`, req);
  }
}
