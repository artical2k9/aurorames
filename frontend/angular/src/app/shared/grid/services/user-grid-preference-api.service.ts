import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ColumnPreferenceEntry { columnKey: string; visible: boolean; order: number; }
export interface UserGridPreferenceDto { moduleKey: string; columns: ColumnPreferenceEntry[]; }

@Injectable({ providedIn: 'root' })
export class UserGridPreferenceApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/users/preferences/grid';

  getPreferences(moduleKey: string): Observable<UserGridPreferenceDto> {
    return this.http.get<UserGridPreferenceDto>(`${this.base}/${moduleKey}`);
  }

  putPreferences(moduleKey: string, entries: ColumnPreferenceEntry[]): Observable<UserGridPreferenceDto> {
    return this.http.put<UserGridPreferenceDto>(`${this.base}/${moduleKey}`, { columns: entries });
  }
}
