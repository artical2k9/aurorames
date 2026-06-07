import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface UnitOfMeasureDto {
  id: string;
  code: string;
  isoCode: string | null;
  name: string;
  category: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateUomRequest {
  code: string;
  isoCode?: string;
  name: string;
  category: string;
}

export interface PatchUomRequest {
  isoCode?: string;
  name?: string;
  active?: boolean;
}

@Injectable({ providedIn: 'root' })
export class PlatformApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBase}/platform/uom`;

  listUom(includeInactive = false): Observable<UnitOfMeasureDto[]> {
    return this.http.get<UnitOfMeasureDto[]>(
      `${this.base}${includeInactive ? '?includeInactive=true' : ''}`
    );
  }

  createUom(req: CreateUomRequest): Observable<UnitOfMeasureDto> {
    return this.http.post<UnitOfMeasureDto>(this.base, req);
  }

  patchUom(code: string, req: PatchUomRequest): Observable<UnitOfMeasureDto> {
    return this.http.patch<UnitOfMeasureDto>(
      `${this.base}/${encodeURIComponent(code)}`, req
    );
  }
}
