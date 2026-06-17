import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  LabourCodeDto, LabourPlanTypeDto, RouteTypeDto, SignificantProcessTypeDto, SupplierDto, WorkCentreDto,
} from '../models/routing.model';

/** CRUD for the routing Settings reference data (US10). All endpoints are org-scoped server-side. */
@Injectable({ providedIn: 'root' })
export class ReferenceDataApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/routing';

  listWorkCentres(): Observable<WorkCentreDto[]> {
    return this.http.get<WorkCentreDto[]>(`${this.base}/work-centres`);
  }

  createWorkCentre(req: WorkCentreDto): Observable<WorkCentreDto> {
    return this.http.post<WorkCentreDto>(`${this.base}/work-centres`, req);
  }

  listLabourCodes(): Observable<LabourCodeDto[]> {
    return this.http.get<LabourCodeDto[]>(`${this.base}/labour-codes`);
  }

  createLabourCode(req: LabourCodeDto): Observable<LabourCodeDto> {
    return this.http.post<LabourCodeDto>(`${this.base}/labour-codes`, req);
  }

  listLabourPlanTypes(): Observable<LabourPlanTypeDto[]> {
    return this.http.get<LabourPlanTypeDto[]>(`${this.base}/labour-plan-types`);
  }

  createLabourPlanType(req: LabourPlanTypeDto): Observable<LabourPlanTypeDto> {
    return this.http.post<LabourPlanTypeDto>(`${this.base}/labour-plan-types`, req);
  }

  listRouteTypes(): Observable<RouteTypeDto[]> {
    return this.http.get<RouteTypeDto[]>(`${this.base}/route-types`);
  }

  createRouteType(req: RouteTypeDto): Observable<RouteTypeDto> {
    return this.http.post<RouteTypeDto>(`${this.base}/route-types`, req);
  }

  listSignificantProcessTypes(): Observable<SignificantProcessTypeDto[]> {
    return this.http.get<SignificantProcessTypeDto[]>(`${this.base}/significant-process-types`);
  }

  createSignificantProcessType(req: SignificantProcessTypeDto): Observable<SignificantProcessTypeDto> {
    return this.http.post<SignificantProcessTypeDto>(`${this.base}/significant-process-types`, req);
  }

  listSuppliers(): Observable<SupplierDto[]> {
    return this.http.get<SupplierDto[]>(`${this.base}/suppliers`);
  }

  createSupplier(req: SupplierDto): Observable<SupplierDto> {
    return this.http.post<SupplierDto>(`${this.base}/suppliers`, req);
  }

  // ── Generic CRUD by resource segment (used by the settings reference grids) ──
  // `resource` is the path segment, e.g. 'work-centres', 'route-types',
  // 'significant-process-types'. All endpoints are org-scoped server-side.
  createRef<T>(resource: string, body: T): Observable<T> {
    return this.http.post<T>(`${this.base}/${resource}`, body);
  }

  updateRef<T>(resource: string, id: string, body: T): Observable<T> {
    return this.http.patch<T>(`${this.base}/${resource}/${id}`, body);
  }

  deleteRef(resource: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${resource}/${id}`);
  }
}
