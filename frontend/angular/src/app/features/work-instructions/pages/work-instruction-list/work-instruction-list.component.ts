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
import { WorkInstructionApiService } from '../../services/work-instruction-api.service';
import { DEFAULT_WORK_INSTRUCTION_COLUMNS } from '../../constants/default-columns';
import { WorkInstructionSummaryDto, RevisionStatus } from '../../models/work-instruction.model';

@Component({
  selector: 'app-work-instruction-list',
  standalone: true,
  imports: [
    CommonModule, AsyncPipe, FormsModule,
    TableModule, InputTextModule, ButtonModule,
    PopoverModule, TagModule, DialogModule, ToastModule,
    ColumnPickerComponent, LucideColumnsSettings, LucideView,
  ],
  providers: [
    MessageService,
    {
      provide: GridPreferenceService,
      useFactory: () =>
        new GridPreferenceService('WORK_INSTRUCTION', DEFAULT_WORK_INSTRUCTION_COLUMNS),
    },
  ],
  template: `
    <p-toast />

    <div class="wil">
      <div class="wil__heading">
        <div class="wil__heading-left">
          <h2 class="wil__title">Work Instructions</h2>
          <span class="wil__count">({{ totalRecords }})</span>
        </div>
        <p-button label="New Work Instruction" icon="pi pi-plus" severity="primary"
                  size="small" (onClick)="openCreate()" />
      </div>

      <div class="wil__toolbar">
        <input pInputText type="text" placeholder="Search identifier or title..."
               [(ngModel)]="searchTerm" (ngModelChange)="onSearchChange()" />
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

        <ng-template pTemplate="body" let-wi let-columns="columns">
          <tr>
            @for (col of visibleColumns(columns); track col.key) {
              <td>
                @switch (col.key) {
                  @case ('revisionStatus') {
                    <p-tag [value]="statusLabel(wi.revisionStatus)"
                           [severity]="statusSeverity(wi.revisionStatus)" />
                  }
                  @default {
                    {{ getCellValue(wi, col) ?? '—' }}
                  }
                }
              </td>
            }
            <td>
              <p-button [rounded]="true" [text]="true" size="small"
                        title="View" aria-label="View"
                        (onClick)="navigateToDetail(wi.id)">
                <svg lucideView [size]="16" [strokeWidth]="1.75"></svg>
              </p-button>
            </td>
          </tr>
        </ng-template>

        <ng-template pTemplate="emptymessage" let-columns>
          <tr>
            <td [attr.colspan]="visibleColumnCount(columns) + 1">No work instructions found.</td>
          </tr>
        </ng-template>
      </p-table>

      <p-dialog header="New Work Instruction" [(visible)]="showCreate" [modal]="true"
                [style]="{ width: '460px' }">
        <div class="wil__form">
          <label>Identifier *</label>
          <input pInputText [(ngModel)]="draft.identifier" placeholder="WI-001" />
          <label>Title *</label>
          <input pInputText [(ngModel)]="draft.title" />
          <label>Description</label>
          <input pInputText [(ngModel)]="draft.description" />
          @if (serverError) {
            <small class="wil__error">{{ serverError }}</small>
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
    .wil { padding: 1.25rem; }
    .wil__heading {
      display: flex; align-items: baseline; justify-content: space-between;
      margin-bottom: 1rem;
    }
    .wil__heading-left { display: flex; align-items: baseline; gap: 0.5rem; }
    .wil__title { margin: 0; font-size: 1.375rem; font-weight: 700; }
    .wil__count { font-size: 0.875rem; color: var(--p-text-muted-color); }
    .wil__toolbar {
      display: flex; align-items: center; gap: 0.5rem;
      flex-wrap: wrap; margin-bottom: 0.75rem;
    }
    .wil__form { display: flex; flex-direction: column; gap: 0.375rem; }
    .wil__form label { font-size: 0.8125rem; font-weight: 600; margin-top: 0.375rem; }
    .wil__error { color: var(--p-red-500); }
  `],
})
export class WorkInstructionListComponent implements OnInit, AfterViewInit {
  private readonly api            = inject(WorkInstructionApiService);
  private readonly router         = inject(Router);
  private readonly messageService = inject(MessageService);
  private readonly destroyRef     = inject(DestroyRef);
  private readonly cdr            = inject(ChangeDetectorRef);
  private readonly udfApi         = inject(UdfApiService);
  readonly gridPreference         = inject(GridPreferenceService);
  private readonly breadcrumbSvc  = inject(BreadcrumbService);

  rows: WorkInstructionSummaryDto[] = [];
  totalRecords = 0;
  loading = true;
  pageSize = 20;
  currentPage = 0;

  searchTerm = '';

  showCreate = false;
  saving = false;
  serverError = '';
  draft: { identifier?: string; title?: string; description?: string } = {};

  private readonly searchSubject = new Subject<string>();

  ngOnInit(): void {
    this.breadcrumbSvc.set([
      { label: 'Engineering' },
      { label: 'Work Instructions' },
    ]);
    this.udfApi.listFields('WORK_INSTRUCTION').pipe(
      map(udfs => udfs.map((u, i): ColumnDef => ({
        key: u.fieldKey,
        label: u.label,
        visible: false,
        order: DEFAULT_WORK_INSTRUCTION_COLUMNS.length + i,
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

  clearFilters(): void {
    this.searchTerm = '';
    this.currentPage = 0;
    this.fetchRows();
  }

  onColumnsApplied(columns: ColumnDef[]): void {
    this.gridPreference.apply(columns);
  }

  getCellValue(wi: WorkInstructionSummaryDto, col: ColumnDef): unknown {
    if (!col.udf) return (wi as unknown as Record<string, unknown>)[col.key];
    const direct = (wi as unknown as Record<string, unknown>)[col.key];
    return direct !== undefined ? direct : wi.customFields?.[col.key];
  }

  visibleColumns(columns: ColumnDef[]): ColumnDef[] {
    return columns.filter(c => c.visible).sort((a, b) => a.order - b.order);
  }

  visibleColumnCount(columns: ColumnDef[]): number {
    return this.visibleColumns(columns).length;
  }

  statusLabel(status: RevisionStatus): string {
    switch (status) {
      case 'APPROVED': return 'Approved';
      case 'PENDING_APPROVAL': return 'Pending';
      default: return 'Draft';
    }
  }

  statusSeverity(status: RevisionStatus): 'success' | 'warn' | 'secondary' {
    switch (status) {
      case 'APPROVED': return 'success';
      case 'PENDING_APPROVAL': return 'warn';
      default: return 'secondary';
    }
  }

  navigateToDetail(id: string): void {
    this.router.navigate(['/engineering/work-instructions', id]);
  }

  openCreate(): void {
    this.draft = {};
    this.serverError = '';
    this.showCreate = true;
    this.api.suggestIdentifier().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: suggestion => { this.draft.identifier = suggestion; this.cdr.detectChanges(); },
    });
  }

  canSave(): boolean {
    return !!(this.draft.identifier && this.draft.title);
  }

  create(): void {
    this.saving = true;
    this.serverError = '';
    this.api.create({
      identifier: this.draft.identifier as string,
      title: this.draft.title as string,
      description: this.draft.description,
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: created => {
        this.saving = false;
        this.showCreate = false;
        this.messageService.add({ severity: 'success', summary: 'Work instruction created' });
        this.cdr.detectChanges();
        this.navigateToDetail(created.id);
      },
      error: err => {
        this.saving = false;
        this.serverError = err?.error?.error ?? 'Failed to create work instruction';
        this.cdr.detectChanges();
      },
    });
  }

  private fetchRows(): void {
    this.loading = true;
    this.api.list({
      page: this.currentPage,
      size: this.pageSize,
      search: this.searchTerm || undefined,
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
