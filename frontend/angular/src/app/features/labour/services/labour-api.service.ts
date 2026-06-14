import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CertificationDto, EmployeeDto, EmployeeProfileDto, EmploymentStatus, Page, SkillDto,
  TrainingHistoryEntryDto,
} from '../models/labour.model';

@Injectable({ providedIn: 'root' })
export class LabourApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/labour';

  // ── Employees ──────────────────────────────────────────────────────────────

  listEmployees(opts: {
    page?: number; size?: number; search?: string; status?: EmploymentStatus;
  } = {}): Observable<Page<EmployeeDto>> {
    let params = new HttpParams()
      .set('page', opts.page ?? 0)
      .set('size', opts.size ?? 20);
    if (opts.search) params = params.set('search', opts.search);
    if (opts.status) params = params.set('status', opts.status);
    return this.http.get<Page<EmployeeDto>>(`${this.base}/employees`, { params });
  }

  getEmployee(id: string): Observable<EmployeeDto> {
    return this.http.get<EmployeeDto>(`${this.base}/employees/${id}`);
  }

  createEmployee(req: Partial<EmployeeDto>): Observable<EmployeeDto> {
    return this.http.post<EmployeeDto>(`${this.base}/employees`, req);
  }

  patchEmployee(id: string, req: Partial<EmployeeDto>): Observable<EmployeeDto> {
    return this.http.patch<EmployeeDto>(`${this.base}/employees/${id}`, req);
  }

  getEmployeeProfile(id: string): Observable<EmployeeProfileDto> {
    return this.http.get<EmployeeProfileDto>(`${this.base}/employees/${id}/profile`);
  }

  getEmployeeTraining(id: string): Observable<Page<TrainingHistoryEntryDto>> {
    return this.http.get<Page<TrainingHistoryEntryDto>>(`${this.base}/employees/${id}/training`);
  }

  createTrainingEvent(req: {
    title: string; trainingDate: string; durationMinutes?: number;
    trainer?: string; notes?: string; skillIds?: string[];
    attendees: { employeeId: string; outcome?: string }[];
  }): Observable<unknown> {
    return this.http.post(`${this.base}/training-events`, req);
  }

  // ── Skills ─────────────────────────────────────────────────────────────────

  listSkills(opts: {
    page?: number; size?: number; search?: string; active?: boolean;
  } = {}): Observable<Page<SkillDto>> {
    let params = new HttpParams()
      .set('page', opts.page ?? 0)
      .set('size', opts.size ?? 20);
    if (opts.search) params = params.set('search', opts.search);
    if (opts.active !== undefined) params = params.set('active', opts.active);
    return this.http.get<Page<SkillDto>>(`${this.base}/skills`, { params });
  }

  createSkill(req: Partial<SkillDto>): Observable<SkillDto> {
    return this.http.post<SkillDto>(`${this.base}/skills`, req);
  }

  patchSkill(id: string, req: Partial<SkillDto>): Observable<SkillDto> {
    return this.http.patch<SkillDto>(`${this.base}/skills/${id}`, req);
  }

  // ── Certifications ─────────────────────────────────────────────────────────

  listCertifications(opts: {
    employeeId?: string; skillId?: string; expiringWithinDays?: number;
  } = {}): Observable<Page<CertificationDto>> {
    let params = new HttpParams();
    if (opts.employeeId) params = params.set('employeeId', opts.employeeId);
    if (opts.skillId) params = params.set('skillId', opts.skillId);
    if (opts.expiringWithinDays !== undefined && opts.expiringWithinDays !== null) {
      params = params.set('expiringWithinDays', opts.expiringWithinDays);
    }
    return this.http.get<Page<CertificationDto>>(`${this.base}/certifications`, { params });
  }

  awardCertification(req: {
    employeeId: string; skillId: string; awardDate: string;
    expiryDate?: string; assessor?: string; evidenceRef?: string;
  }): Observable<CertificationDto> {
    return this.http.post<CertificationDto>(`${this.base}/certifications`, req);
  }

  revokeCertification(id: string, reason: string): Observable<CertificationDto> {
    return this.http.post<CertificationDto>(
      `${this.base}/certifications/${id}/revoke`, { reason });
  }
}
