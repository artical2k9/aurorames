import {
  AfterViewInit, ChangeDetectorRef, Component, DestroyRef, inject, OnInit,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  Subject, debounceTime, distinctUntilChanged, switchMap, catchError, map, of,
} from 'rxjs';
import { AsyncPipe, CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { InputTextModule } from 'primeng/inputtext';
import { AutoCompleteModule, AutoCompleteSelectEvent } from 'primeng/autocomplete';
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
import { ItemMasterApiService } from '../../../item-master/services/item-master-api.service';
import { ItemMasterDto } from '../../../item-master/models/item-master.model';
import { InspectionPlanApiService } from '../../services/inspection-plan-api.service';
import { DEFAULT_INSPECTION_PLAN_COLUMNS } from '../../constants/default-columns';
import { InspectionPlanSummaryDto, RevisionStatus } from '../../models/inspection-plan.model';

@Component({
  selector: 'app-inspection-plan-list',
  standalone: true,
  imports: [
    CommonModule, AsyncPipe, FormsModule,
    TableModule, InputTextModule, AutoCompleteModule, ButtonModule,
    PopoverModule, TagModule, DialogModule, ToastModule,
    ColumnPickerComponent, LucideColumnsSettings, LucideView,
  ],
  providers: [
    MessageService,
    {
      provide: GridPreferenceService,
      useFactory: () => new GridPreferenceService('INSPECTION_PLAN', DEFAULT_INSPECTION_PLAN_COLUMNS),
    },
  ],
  template: `
    <p-toast />

    <div class="ipl">
      <div class="ipl__heading">
        <div class="ipl__heading-left">
          <h2 class="ipl__title">Inspection Plans</h2>
          <span class="ipl__count">({{ totalRecords }} plans)</span>
        </div>
        <p-button label="New Plan" icon="pi pi-plus" severity="primary"
                  size="small" (onClick)="openCreate()" />
      </div>

      <div class="ipl__toolbar">
        <input pInputText type="text" placeholder="Search part number or name..."
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

        <ng-template pTemplate="body" let-plan let-columns="columns">
          <tr>
            @for (col of visibleColumns(columns); track col.key) {
              <td>
                @switch (col.key) {
                  @case ('revisionStatus') {
                    <p-tag [value]="statusLabel(plan.revisionStatus)"
                           [severity]="statusSeverity(plan.revisionStatus)" />
                  }
                  @default {
                    {{ getCellValue(plan, col) ?? '—' }}
                  }
                }
              </td>
            }
            <td>
              <p-button [rounded]="true" [text]="true" size="small"
                        title="View" aria-label="View"
                        (onClick)="navigateToDetail(plan.id)">
                <svg lucideView [size]="16" [strokeWidth]="1.75"></svg>
              </p-button>
            </td>
          </tr>
        </ng-template>

        <ng-template pTemplate="emptymessage" let-columns>
          <tr>
            <td [attr.colspan]="visibleColumnCount(columns) + 1">No inspection plans found.</td>
          </tr>
        </ng-template>
      </p-table>

      <p-dialog header="New Inspection Plan" [(visible)]="showCreate" [modal]="true"
                [style]="{ width: '460px' }">
        <div class="ipl__form">
          <label>Part Number *</label>
          <p-autocomplete [(ngModel)]="partNumberModel"
                          [suggestions]="itemSuggestions"
                          field="partNumber"
                          placeholder="Search approved part number..."
                          [dropdown]="false"
                          [minLength]="1"
                          (completeMethod)="onSearchItem($event)"
                          (onSelect)="onItemSelect($event)">
            <ng-template #item let-item>
              <div class="ipl__suggestion">
                <span class="ipl__suggestion-pn">{{ item.partNumber }}</span>
                <span class="ipl__suggestion-rev">Rev {{ item.revision }}</span>
                <span class="ipl__suggestion-desc">— {{ item.description }}</span>
              </div>
            </ng-template>
          </p-autocomplete>
          @if (selectedItemLabel) {
            <small class="ipl__hint">Plan will be linked to {{ selectedItemLabel }}
              (current revision shown for reference only).</small>
          }
          <label>Plan Name *</label>
          <input pInputText [(ngModel)]="draft.name" />
          <label>Description</label>
          <input pInputText [(ngModel)]="draft.description" />
          @if (serverError) {
            <small class="ipl__error">{{ serverError }}</small>
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
    .ipl { padding: 1.25rem; }
    .ipl__heading {
      display: flex; align-items: baseline; justify-content: space-between;
      margin-bottom: 1rem;
    }
    .ipl__heading-left { display: flex; align-items: baseline; gap: 0.5rem; }
    .ipl__title { margin: 0; font-size: 1.375rem; font-weight: 700; }
    .ipl__count { font-size: 0.875rem; color: var(--p-text-muted-color); }
    .ipl__toolbar {
      display: flex; align-items: center; gap: 0.5rem;
      flex-wrap: wrap; margin-bottom: 0.75rem;
    }
    .ipl__form { display: flex; flex-direction: column; gap: 0.375rem; }
    .ipl__form label { font-size: 0.8125rem; font-weight: 600; margin-top: 0.375rem; }
    .ipl__error { color: var(--p-red-500); }
    .ipl__hint { font-size: 0.75rem; color: var(--p-text-muted-color); }
    .ipl__suggestion { display: flex; align-items: baseline; gap: 0.5rem; }
    .ipl__suggestion-pn { font-weight: 600; }
    .ipl__suggestion-rev { font-size: 0.75rem; color: var(--p-text-muted-color); }
    .ipl__suggestion-desc { font-size: 0.8125rem; color: var(--p-text-muted-color); }
    :host ::ng-deep .ipl__form p-autocomplete,
    :host ::ng-deep .ipl__form .p-autocomplete { width: 100%; }
    :host ::ng-deep .ipl__form .p-autocomplete-input { width: 100%; }
  `],
})
export class InspectionPlanListComponent implements OnInit, AfterViewInit {
  private readonly api            = inject(InspectionPlanApiService);
  private readonly router         = inject(Router);
  private readonly messageService = inject(MessageService);
  private readonly destroyRef     = inject(DestroyRef);
  private readonly cdr            = inject(ChangeDetectorRef);
  private readonly udfApi         = inject(UdfApiService);
  private readonly itemApi        = inject(ItemMasterApiService);
  readonly gridPreference         = inject(GridPreferenceService);
  private readonly breadcrumbSvc  = inject(BreadcrumbService);

  rows: InspectionPlanSummaryDto[] = [];
  totalRecords = 0;
  loading = true;
  pageSize = 20;
  currentPage = 0;

  searchTerm = '';

  showCreate = false;
  saving = false;
  serverError = '';
  draft: { itemId?: string; name?: string; description?: string } = {};

  itemSuggestions: ItemMasterDto[] = [];
  partNumberModel: ItemMasterDto | string = '';
  selectedItemLabel = '';

  private readonly searchSubject = new Subject<string>();
  private readonly searchItemSubject = new Subject<string>();

  ngOnInit(): void {
    this.breadcrumbSvc.set([
      { label: 'Quality' },
      { label: 'Inspection Plans' },
    ]);
    this.udfApi.listFields('INSPECTION_PLAN').pipe(
      map(udfs => udfs.map((u, i): ColumnDef => ({
        key: u.fieldKey,
        label: u.label,
        visible: false,
        order: DEFAULT_INSPECTION_PLAN_COLUMNS.length + i,
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
    this.searchItemSubject.pipe(
      debounceTime(250),
      distinctUntilChanged(),
      switchMap(q => this.itemApi.list({ search: q, page: 0, size: 10, revisionStatus: 'APPROVED' })
        .pipe(catchError(() => of({ content: [], totalElements: 0 })))),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(page => {
      this.itemSuggestions = page.content;
      this.cdr.detectChanges();
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

  getCellValue(plan: InspectionPlanSummaryDto, col: ColumnDef): unknown {
    if (!col.udf) return (plan as unknown as Record<string, unknown>)[col.key];
    const direct = (plan as unknown as Record<string, unknown>)[col.key];
    return direct !== undefined ? direct : plan.customFields?.[col.key];
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
    this.router.navigate(['/quality/inspection-plans', id]);
  }

  openCreate(): void {
    this.draft = {};
    this.partNumberModel = '';
    this.selectedItemLabel = '';
    this.itemSuggestions = [];
    this.serverError = '';
    this.showCreate = true;
  }

  onSearchItem(event: { query: string }): void {
    // A fresh search invalidates any prior selection until the user picks again.
    this.draft.itemId = undefined;
    this.selectedItemLabel = '';
    this.searchItemSubject.next(event.query);
  }

  onItemSelect(event: AutoCompleteSelectEvent): void {
    const item = event.value as ItemMasterDto;
    this.draft.itemId = item.id;
    this.selectedItemLabel = `${item.partNumber} (Rev ${item.revision})`;
  }

  canSave(): boolean {
    return !!(this.draft.itemId && this.draft.name);
  }

  create(): void {
    this.saving = true;
    this.serverError = '';
    this.api.createPlan({
      itemId: this.draft.itemId as string,
      name: this.draft.name as string,
      description: this.draft.description,
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: created => {
        this.saving = false;
        this.showCreate = false;
        this.messageService.add({ severity: 'success', summary: 'Inspection plan created' });
        this.cdr.detectChanges();
        this.navigateToDetail(created.id);
      },
      error: err => {
        this.saving = false;
        this.serverError = err?.error?.error ?? 'Failed to create inspection plan';
        this.cdr.detectChanges();
      },
    });
  }

  private fetchRows(): void {
    this.loading = true;
    this.api.listPlans({
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
