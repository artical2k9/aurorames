import { inject } from '@angular/core';
import { BehaviorSubject, catchError, map, of, tap } from 'rxjs';
import { ColumnDef } from '../models/column-def.model';
import { ColumnPreferenceEntry, UserGridPreferenceApiService } from './user-grid-preference-api.service';

export class GridPreferenceService {
  private readonly api = inject(UserGridPreferenceApiService);
  readonly activeColumns$ = new BehaviorSubject<ColumnDef[]>([]);

  private dynamicColumns: ColumnDef[] = [];

  constructor(
    private readonly moduleKey: string,
    private readonly defaultColumns: ColumnDef[],
  ) {}

  load(dynamicColumns: ColumnDef[] = []): void {
    this.dynamicColumns = dynamicColumns;
    const allDefaults = [...this.defaultColumns, ...dynamicColumns];
    this.api.getPreferences(this.moduleKey).pipe(
      map(dto => this.mergeWithDefaults(dto.columns, allDefaults)),
      catchError(() => of(allDefaults)),
    ).subscribe(cols => this.activeColumns$.next(cols));
  }

  apply(columns: ColumnDef[]): void {
    const entries = this.toEntries(columns);
    this.api.putPreferences(this.moduleKey, entries).pipe(
      tap(() => this.activeColumns$.next(columns)),
    ).subscribe();
  }

  reset(): void {
    const allDefaults = [...this.defaultColumns, ...this.dynamicColumns];
    const entries = this.toEntries(allDefaults);
    this.api.putPreferences(this.moduleKey, entries).pipe(
      tap(() => this.activeColumns$.next(allDefaults)),
    ).subscribe();
  }

  private mergeWithDefaults(entries: ColumnPreferenceEntry[], allDefaults: ColumnDef[]): ColumnDef[] {
    return allDefaults
      .map(def => {
        const saved = entries.find(e => e.columnKey === def.key);
        return saved ? { ...def, visible: saved.visible, order: saved.order } : def;
      })
      .sort((a, b) => a.order - b.order);
  }

  private toEntries(columns: ColumnDef[]): ColumnPreferenceEntry[] {
    return columns.map(c => ({ columnKey: c.key, visible: c.visible, order: c.order }));
  }
}
