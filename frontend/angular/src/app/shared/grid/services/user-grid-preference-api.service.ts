import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ColumnDef } from '../models/column-def.model';

@Injectable({ providedIn: 'root' })
export class UserGridPreferenceApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/users/preferences/grid';

  getPreferences(moduleKey: string): Observable<ColumnDef[]> {
    return this.http.get<ColumnDef[]>(`${this.base}/${moduleKey}`);
  }

  putPreferences(moduleKey: string, columns: ColumnDef[]): Observable<void> {
    return this.http.put<void>(`${this.base}/${moduleKey}`, columns);
  }
}
