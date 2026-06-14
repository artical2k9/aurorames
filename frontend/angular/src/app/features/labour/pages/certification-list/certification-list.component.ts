import {
  AfterViewInit, ChangeDetectorRef, Component, DestroyRef, inject, OnInit,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, map, of } from 'rxjs';
import { AsyncPipe, CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TableModule } from 'primeng/table';
import { SelectModule } from 'primeng/select';
import { ButtonModule } from 'primeng/button';
import { PopoverModule } from 'primeng/popover';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { LucideColumnsSettings } from '@lucide/angular';
import { GridPreferenceService, ColumnPickerComponent, ColumnDef } from '../../../../shared/grid';
import { BreadcrumbService } from '../../../../shared/ui';
import { UdfApiService } from '../../../../shared/udf/udf-api.service';
import { LabourApiService } from '../../services/labour-api.service';
import { DEFAULT_CERTIFICATION_COLUMNS } from '../../constants/default-columns';
import { CertificationDto, CertificationState } from '../../models/labour.model';

@Component({
  selector: 'app-certification-list',
  standalone: true,
  imports: [
    CommonModule, AsyncPipe, FormsModule,
    TableModule, SelectModule, ButtonModule, PopoverModule, TagModule, ToastModule,
    ColumnPickerComponent, LucideColumnsSettings,
  ],
  providers: [
    MessageService,
    {
      provide: GridPreferenceService,
      useFactory: () =>
        new GridPreferenceService('CERTIFICATION', DEFAULT_CERTIFICATION_COLUMNS),
    },
  ],
  template: `
    <p-toast />

    <div class="cl">
      <div class="cl__heading">
        <div class="cl__heading-left">
          <h2 class="cl__title">Certifications</h2>
          <span class="cl__count">({{ rows.length }} shown)</span>
        </div>
        <div class="cl__toolbar">
          <p-select [options]="expiryWindowOptions" [(ngModel)]="expiringWithinDays"
                    optionLabel="label" optionValue="value"
                    placeholder="Expiry window" [showClear]="true"
                    (onChange)="reload()" styleClass="cl__window-filter" />
          <p-button [rounded]="true" [text]="true"
                    aria-label="Customise columns" (onClick)="colPickerPanel.toggle($event)">
            <svg lucideColumnsSettings [size]="16" [strokeWidth]="1.75"></svg>
          </p-button>
        </div>
      </div>

      <p-popover #colPickerPanel>
        <app-column-picker
          [columns]="(gridPreference.activeColumns$ | async) ?? []"
          (applied)="onColumnsApplied($event); colPickerPanel.hide()"
          (cancelled)="colPickerPanel.hide()"
          (reset)="gridPreference.reset(); colPickerPanel.hide()"
        />
      </p-popover>

      <p-table [columns]="(gridPreference.activeColumns$ | async) ?? []"
               [value]="rows" [paginator]="true" [rows]="20"
               [loading]="loading"
               styleClass="p-datatable-gridlines p-datatable-sm">
        <ng-template pTemplate="header" let-columns>
          <tr>
            @for (col of visibleColumns(columns); track col.key) {
              <th>{{ col.label }}</th>
            }
            <th style="width:6rem">Actions</th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-cert let-columns="columns">
          <tr>
            @for (col of visibleColumns(columns); track col.key) {
              <td>
                @switch (col.key) {
                  @case ('state') {
                    <p-tag [value]="stateLabel(cert.state)" [severity]="stateSeverity(cert.state)" />
                  }
                  @case ('expiryDate') {
                    {{ cert.expiryDate || 'Never' }}
                  }
                  @default {
                    {{ getCellValue(cert, col) ?? '—' }}
                  }
                }
              </td>
            }
            <td>
              <p-button label="Employee" [text]="true" size="small"
                        (onClick)="navigateToEmployee(cert.employeeId)" />
            </td>
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage" let-columns>
          <tr><td [attr.colspan]="visibleColumnCount(columns) + 1">No certifications found.</td></tr>
        </ng-template>
      </p-table>
    </div>
  `,
  styles: [`
    .cl { padding: 1.25rem; }
    .cl__heading {
      display: flex; align-items: center; justify-content: space-between;
      margin-bottom: 1rem;
    }
    .cl__heading-left { display: flex; align-items: baseline; gap: 0.5rem; }
    .cl__title { margin: 0; font-size: 1.375rem; font-weight: 700; }
    .cl__count { font-size: 0.875rem; color: var(--p-text-muted-color); }
    .cl__toolbar { display: flex; align-items: center; gap: 0.5rem; }
    :host ::ng-deep .cl__window-filter { min-width: 200px; }
  `],
})
export class CertificationListComponent implements OnInit, AfterViewInit {
  private readonly api = inject(LabourApiService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly udfApi = inject(UdfApiService);
  readonly gridPreference = inject(GridPreferenceService);
  private readonly breadcrumbSvc = inject(BreadcrumbService);

  rows: CertificationDto[] = [];
  loading = true;
  expiringWithinDays: number | null = null;

  readonly expiryWindowOptions = [
    { label: 'Expiring within 30 days', value: 30 },
    { label: 'Expiring within 60 days', value: 60 },
    { label: 'Expiring within 90 days', value: 90 },
  ];

  ngOnInit(): void {
    this.breadcrumbSvc.set([
      { label: 'Labour' },
      { label: 'Certifications' },
    ]);
    this.udfApi.listFields('CERTIFICATION').pipe(
      map(udfs => udfs.map((u, i): ColumnDef => ({
        key: u.fieldKey,
        label: u.label,
        visible: false,
        order: DEFAULT_CERTIFICATION_COLUMNS.length + i,
        udf: true,
      }))),
      catchError(() => of([])),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: udfCols => {
        this.gridPreference.load(udfCols);
        this.cdr.detectChanges();
      },
    });
  }

  ngAfterViewInit(): void {
    setTimeout(() => this.fetchRows());
  }

  reload(): void {
    this.fetchRows();
  }

  onColumnsApplied(columns: ColumnDef[]): void {
    this.gridPreference.apply(columns);
  }

  getCellValue(cert: CertificationDto, col: ColumnDef): unknown {
    if (!col.udf) return (cert as unknown as Record<string, unknown>)[col.key];
    const direct = (cert as unknown as Record<string, unknown>)[col.key];
    return direct !== undefined ? direct : cert.customFields?.[col.key];
  }

  visibleColumns(columns: ColumnDef[]): ColumnDef[] {
    return columns.filter(c => c.visible).sort((a, b) => a.order - b.order);
  }

  visibleColumnCount(columns: ColumnDef[]): number {
    return this.visibleColumns(columns).length;
  }

  navigateToEmployee(employeeId: string): void {
    this.router.navigate(['/labour/employees', employeeId]);
  }

  stateLabel(state: CertificationState): string {
    switch (state) {
      case 'EXPIRING_SOON': return 'Expiring Soon';
      case 'EXPIRED':       return 'Expired';
      case 'REVOKED':       return 'Revoked';
      default:              return 'Active';
    }
  }

  stateSeverity(state: CertificationState): 'success' | 'warn' | 'danger' | 'secondary' {
    switch (state) {
      case 'EXPIRING_SOON': return 'warn';
      case 'EXPIRED':       return 'danger';
      case 'REVOKED':       return 'secondary';
      default:              return 'success';
    }
  }

  private fetchRows(): void {
    this.loading = true;
    this.api.listCertifications({
      expiringWithinDays: this.expiringWithinDays ?? undefined,
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: page => {
        this.rows = page.content;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.rows = [];
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }
}
