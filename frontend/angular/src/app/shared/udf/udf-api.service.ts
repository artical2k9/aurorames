import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export type UdfFieldType = 'TEXT' | 'NUMBER' | 'DATE' | 'BOOLEAN' | 'LIST';

export interface UdfFieldDefinition {
  id: string;
  fieldKey: string;
  label: string;
  fieldType: UdfFieldType;
  required: boolean;
  defaultValue?: string;
  listOptions?: string[];
  validationRules?: Record<string, unknown>;
  displayOrder: number;
}

@Injectable({ providedIn: 'root' })
export class UdfApiService {
  private readonly http = inject(HttpClient);

  listFields(moduleKey: string): Observable<UdfFieldDefinition[]> {
    const params = new HttpParams().set('module', moduleKey);
    return this.http.get<UdfFieldDefinition[]>('/api/v1/udf/fields', { params });
  }
}
