import { inject } from '@angular/core';
import { BehaviorSubject, catchError, of, tap } from 'rxjs';
import { ColumnDef } from '../models/column-def.model';
import { UserGridPreferenceApiService } from './user-grid-preference-api.service';

export class GridPreferenceService {
  private readonly api = inject(UserGridPreferenceApiService);
  readonly activeColumns$ = new BehaviorSubject<ColumnDef[]>([]);

  constructor(
    private readonly moduleKey: string,
    private readonly defaultColumns: ColumnDef[],
  ) {}

  load(): void {
    this.api.getPreferences(this.moduleKey).pipe(
      catchError(() => of(this.defaultColumns)),
    ).subscribe(cols => this.activeColumns$.next(cols));
  }

  apply(columns: ColumnDef[]): void {
    this.api.putPreferences(this.moduleKey, columns).pipe(
      tap(() => this.activeColumns$.next(columns)),
    ).subscribe();
  }

  reset(): void {
    this.api.putPreferences(this.moduleKey, this.defaultColumns).pipe(
      tap(() => this.activeColumns$.next(this.defaultColumns)),
    ).subscribe();
  }
}
