import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { EcoDto, CreateEcoRequest, Page } from '../models/eco.model';

@Injectable({ providedIn: 'root' })
export class EcoApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/ecos';

  list(page = 0, size = 20): Observable<Page<EcoDto>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<EcoDto>>(this.base, { params });
  }

  getById(id: string): Observable<EcoDto> {
    return this.http.get<EcoDto>(`${this.base}/${id}`);
  }

  create(req: CreateEcoRequest): Observable<EcoDto> {
    return this.http.post<EcoDto>(this.base, req);
  }

  approve(ecoId: string): Observable<EcoDto> {
    return this.http.post<EcoDto>(`${this.base}/${ecoId}/approve`, {});
  }
}
