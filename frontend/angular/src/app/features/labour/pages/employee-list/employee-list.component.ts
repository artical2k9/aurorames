import {
  AfterViewInit, ChangeDetectorRef, Component, DestroyRef, inject, OnInit,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, debounceTime, distinctUntilChanged, catchError, map, of } from 'rxjs';
import { AsyncPipe, CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { ButtonModule } from 'primeng/button';
import { PopoverModule } from 'primeng/popover';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { LucideColumnsSettings, LucideView } from '@lucide/angular';
import { GridPreferenceService, ColumnPickerComponent, ColumnDef } from '../../../../shared/grid';
import { BreadcrumbService } from '../../../../shared/ui';
import { UdfApiService } from '../../../../shared/udf/udf-api.service';
import { LabourApiService } from '../../services/labour-api.service';
import { DEFAULT_EMPLOYEE_COLUMNS } from '../../constants/default-columns';
import { EmployeeDto, EmploymentStatus } from '../../models/labour.model';

@Component({
  selector: 'app-employee-list',
  standalone: true,
  imports: [
    CommonModule, AsyncPipe, FormsModule,
    TableModule, InputTextModule, SelectModule, ButtonModule,
    PopoverModule, TagModule, DialogModule, ToastModule,
    ColumnPickerComponent, LucideColumnsSettings, LucideView,
  ],
  providers: [
    MessageService,
    {
      provide: GridPreferenceService,
      useFactory: () => new GridPreferenceService('EMPLOYEE', DEFAULT_EMPLOYEE_COLUMNS),
    },
  ],
  template: `
    <p-toast />

    <div class="eml">
      <div class="eml__heading">
        <div class="eml__heading-left">
          <h2 class="eml__title">Employees</h2>
          <span class="eml__count">({{ totalRecords }} employees)</span>
        </div>
        <p-button label="New Employee" icon="pi pi-plus" severity="primary"
                  size="small" (onClick)="openCreate()" />
      </div>

      <div class="eml__toolbar">
        <input pInputText type="text" placeholder="Search number, name or email..."
               [(ngModel)]="searchTerm" (ngModelChange)="onSearchChange()" />

        <p-select [options]="statusOptions" [(ngModel)]="selectedStatus"
                  optionLabel="label" optionValue="value"
                  placeholder="Status" [showClear]="true"
                  (onChange)="reload()" styleClass="filter-select" />

        <p-button label="Clear" severity="secondary" size="small" (onClick)="clearFilters()" />

        <p-button [rounded]="true" [text]="true"
                  aria-label="Customise columns" (onClick)="colPickerPanel.toggle($event)">
          <svg lucideColumnsSettings [size]="16" [strokeWidth]="1.75"></svg>
        </p-button>
      </div>

      <p-popover #colPickerPanel>
        <app-column-picker
          [columns]="(gridPreference.activeColumns$ | async) ?? []"
          (applied)="onColumnsApplied($event); colPickerPanel.hide()"
          (cancelled)="colPickerPanel.hide()"
          (reset)="gridPreference.reset(); colPickerPanel.hide()"
        />
      </p-popover>

      <p-table
        [columns]="(gridPreference.activeColumns$ | async) ?? []"
        [value]="rows"
        [lazy]="true"
        [lazyLoadOnInit]="false"
        [paginator]="true"
        [rows]="pageSize"
        [totalRecords]="totalRecords"
        (onLazyLoad)="onLazyLoad($event)"
        [loading]="loading"
        dataKey="id"
        styleClass="p-datatable-gridlines p-datatable-sm"
      >
        <ng-template pTemplate="header" let-columns>
          <tr>
            @for (col of visibleColumns(columns); track col.key) {
              <th>{{ col.label }}</th>
            }
            <th style="width:6rem">Actions</th>
          </tr>
        </ng-template>

        <ng-template pTemplate="body" let-employee let-columns="columns">
          <tr>
            @for (col of visibleColumns(columns); track col.key) {
              <td>
                @switch (col.key) {
                  @case ('employmentStatus') {
                    <p-tag [value]="employee.employmentStatus === 'ACTIVE' ? 'Active' : 'Inactive'"
                           [severity]="employee.employmentStatus === 'ACTIVE' ? 'success' : 'secondary'" />
                  }
                  @default {
                    {{ getCellValue(employee, col) ?? '—' }}
                  }
                }
              </td>
            }
            <td>
              <p-button [rounded]="true" [text]="true" size="small"
                        title="View" aria-label="View"
                        (onClick)="navigateToDetail(employee.id)">
                <svg lucideView [size]="16" [strokeWidth]="1.75"></svg>
              </p-button>
            </td>
          </tr>
        </ng-template>

        <ng-template pTemplate="emptymessage" let-columns>
          <tr>
            <td [attr.colspan]="visibleColumnCount(columns) + 1">No employees found.</td>
          </tr>
        </ng-template>
      </p-table>

      <!-- Create dialog -->
      <p-dialog header="New Employee" [(visible)]="showCreate" [modal]="true"
                [style]="{ width: '420px' }">
        <div class="eml__form">
          <label>Employee Number *</label>
          <input pInputText [(ngModel)]="draft.employeeNumber" />
          <label>First Name *</label>
          <input pInputText [(ngModel)]="draft.firstName" />
          <label>Last Name *</label>
          <input pInputText [(ngModel)]="draft.lastName" />
          <label>Email</label>
          <input pInputText [(ngModel)]="draft.email" />
          @if (serverError) {
            <small class="eml__error">{{ serverError }}</small>
          }
        </div>
        <ng-template pTemplate="footer">
          <p-button label="Cancel" severity="secondary" size="small"
                    (onClick)="showCreate = false" />
          <p-button label="Create" severity="primary" size="small"
                    [loading]="saving" [disabled]="!canSave()"
                    (onClick)="create()" />
        </ng-template>
      </p-dialog>
    </div>
  `,
  styles: [`
    .eml { padding: 1.25rem; }
    .eml__heading {
      display: flex; align-items: baseline; justify-content: space-between;
      margin-bottom: 1rem;
    }
    .eml__heading-left { display: flex; align-items: baseline; gap: 0.5rem; }
    .eml__title { margin: 0; font-size: 1.375rem; font-weight: 700; }
    .eml__count { font-size: 0.875rem; color: var(--p-text-muted-color); }
    .eml__toolbar {
      display: flex; align-items: center; gap: 0.5rem;
      flex-wrap: wrap; margin-bottom: 0.75rem;
    }
    :host ::ng-deep .filter-select { min-width: 150px; }
    .eml__form { display: flex; flex-direction: column; gap: 0.375rem; }
    .eml__form label { font-size: 0.8125rem; font-weight: 600; margin-top: 0.375rem; }
    .eml__error { color: var(--p-red-500); }
  `],
})
export class EmployeeListComponent implements OnInit, AfterViewInit {
  private readonly api            = inject(LabourApiService);
  private readonly router         = inject(Router);
  private readonly messageService = inject(MessageService);
  private readonly destroyRef     = inject(DestroyRef);
  private readonly cdr            = inject(ChangeDetectorRef);
  private readonly udfApi         = inject(UdfApiService);
  readonly gridPreference         = inject(GridPreferenceService);
  private readonly breadcrumbSvc  = inject(BreadcrumbService);

  rows: EmployeeDto[] = [];
  totalRecords = 0;
  loading = true;
  pageSize = 20;
  currentPage = 0;

  searchTerm = '';
  selectedStatus: EmploymentStatus | null = null;

  showCreate = false;
  saving = false;
  serverError = '';
  draft: Partial<EmployeeDto> = {};

  private readonly searchSubject = new Subject<string>();

  readonly statusOptions = [
    { label: 'Active',   value: 'ACTIVE' },
    { label: 'Inactive', value: 'INACTIVE' },
  ];

  ngOnInit(): void {
    this.breadcrumbSvc.set([
      { label: 'Labour' },
      { label: 'Employees' },
    ]);
    this.udfApi.listFields('EMPLOYEE').pipe(
      map(udfs => udfs.map((u, i): ColumnDef => ({
        key: u.fieldKey,
        label: u.label,
        visible: false,
        order: DEFAULT_EMPLOYEE_COLUMNS.length + i,
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
    this.searchSubject.pipe(
      debounceTime(400),
      distinctUntilChanged(),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => {
      this.currentPage = 0;
      this.fetchRows();
    });
  }

  ngAfterViewInit(): void {
    setTimeout(() => this.fetchRows());
  }

  onLazyLoad(event: TableLazyLoadEvent): void {
    this.currentPage = (event.first ?? 0) / (event.rows ?? this.pageSize);
    this.pageSize = event.rows ?? this.pageSize;
    this.fetchRows();
  }

  onSearchChange(): void {
    this.searchSubject.next(this.searchTerm);
  }

  reload(): void {
    this.currentPage = 0;
    this.fetchRows();
  }

  clearFilters(): void {
    this.searchTerm = '';
    this.selectedStatus = null;
    this.reload();
  }

  onColumnsApplied(columns: ColumnDef[]): void {
    this.gridPreference.apply(columns);
  }

  getCellValue(employee: EmployeeDto, col: ColumnDef): unknown {
    if (!col.udf) return (employee as unknown as Record<string, unknown>)[col.key];
    const direct = (employee as unknown as Record<string, unknown>)[col.key];
    return direct !== undefined ? direct : employee.customFields?.[col.key];
  }

  visibleColumns(columns: ColumnDef[]): ColumnDef[] {
    return columns.filter(c => c.visible).sort((a, b) => a.order - b.order);
  }

  visibleColumnCount(columns: ColumnDef[]): number {
    return this.visibleColumns(columns).length;
  }

  navigateToDetail(id: string): void {
    this.router.navigate(['/labour/employees', id]);
  }

  openCreate(): void {
    this.draft = {};
    this.serverError = '';
    this.showCreate = true;
  }

  canSave(): boolean {
    return !!(this.draft.employeeNumber && this.draft.firstName && this.draft.lastName);
  }

  create(): void {
    this.saving = true;
    this.serverError = '';
    this.api.createEmployee(this.draft)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving = false;
          this.showCreate = false;
          this.messageService.add({ severity: 'success', summary: 'Employee created' });
          this.fetchRows();
          this.cdr.detectChanges();
        },
        error: err => {
          this.saving = false;
          this.serverError = err?.error?.error ?? 'Failed to create employee';
          this.cdr.detectChanges();
        },
      });
  }

  private fetchRows(): void {
    this.loading = true;
    this.api.listEmployees({
      page: this.currentPage,
      size: this.pageSize,
      search: this.searchTerm || undefined,
      status: this.selectedStatus ?? undefined,
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: page => {
        this.rows = page.content;
        this.totalRecords = page.totalElements;
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
