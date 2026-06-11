import { AfterViewInit, ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AsyncPipe } from '@angular/common';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, switchMap, catchError, map, of } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { MessageModule } from 'primeng/message';
import { PopoverModule } from 'primeng/popover';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { AutoCompleteModule, AutoCompleteCompleteEvent } from 'primeng/autocomplete';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { LucideColumnsSettings, LucideView, LucideFilePenLine, LucideFilePlus } from '@lucide/angular';
import { BreadcrumbService } from '../../../../shared/ui';
import { GridPreferenceService, ColumnPickerComponent, ColumnDef } from '../../../../shared/grid';
import { UdfApiService } from '../../../../shared/udf/udf-api.service';
import { BomApiService } from '../../services/bom-api.service';
import { BomSummaryDto, RevisionStatus } from '../../models/bom.model';
import { ItemMasterApiService } from '../../../item-master/services/item-master-api.service';
import { ItemMasterDto } from '../../../item-master/models/item-master.model';

const DEFAULT_BOM_BROWSER_COLUMNS: ColumnDef[] = [
  { key: 'partNumber',         label: 'Part Number',     visible: true,  order: 0, locked: true },
  { key: 'itemRevision',       label: 'Item Rev',        visible: true,  order: 1, locked: true },
  { key: 'description',        label: 'BOM Description', visible: true,  order: 2 },
  { key: 'revision',           label: 'BOM Rev',         visible: true,  order: 3 },
  { key: 'revisionStatus',     label: 'BOM Status',      visible: true,  order: 4 },
  { key: 'itemRevisionStatus', label: 'Item Status',     visible: true,  order: 5 },
  { key: 'hasDraft',           label: 'Has Draft',       visible: false, order: 6 },
  { key: 'createdBy',          label: 'Created By',      visible: false, order: 7 },
];

@Component({
  selector: 'app-bom-browser',
  standalone: true,
  imports: [
    AsyncPipe, FormsModule,
    TableModule, ButtonModule, InputTextModule, MessageModule, PopoverModule, TagModule,
    DialogModule, AutoCompleteModule, ToastModule,
    ColumnPickerComponent,
    LucideColumnsSettings, LucideView, LucideFilePenLine, LucideFilePlus,
  ],
  providers: [
    MessageService,
    {
      provide: GridPreferenceService,
      useFactory: () => new GridPreferenceService('BOM_BROWSER', DEFAULT_BOM_BROWSER_COLUMNS),
    },
  ],
  template: `
    <p-toast />

    <div class="bb">

      <div class="bb__heading">
        <div class="bb__heading-left">
          <h2 class="bb__title">Bills of Materials</h2>
          <span class="bb__count">({{ totalRecords }} BOMs)</span>
        </div>
        <p-button label="Create BOM" icon="pi pi-plus" severity="primary"
                  size="small" (onClick)="openCreateDialog()" />
      </div>

      <div class="bb__toolbar">
        <input pInputText
               class="bb__search"
               placeholder="Search by part number or description…"
               [(ngModel)]="searchTerm"
               (ngModelChange)="onSearch()" />

        <p-button [rounded]="true" [text]="true"
                  aria-label="Customise columns" (onClick)="colPickerPanel.toggle($event)">
          <svg lucideColumnsSettings [size]="16" [strokeWidth]="1.75"></svg>
        </p-button>
      </div>

      @if (error) {
        <p-message severity="error" [text]="error" styleClass="bb__error" />
      }

      <p-popover #colPickerPanel>
        <app-column-picker
          [columns]="(gridPreference.activeColumns$ | async) ?? []"
          (applied)="onColumnsApplied($event); colPickerPanel.hide()"
          (cancelled)="colPickerPanel.hide()"
          (reset)="gridPreference.reset(); colPickerPanel.hide()"
        />
      </p-popover>

      <!-- Create BOM dialog -->
      <p-dialog header="New BOM" [(visible)]="showCreateDialog" [modal]="true"
                [style]="{ width: '460px' }" (onHide)="resetCreateForm()">
        @if (createError) {
          <p-message severity="error" [text]="createError" styleClass="bb__dialog-error" />
        }
        <div class="bb__field">
          <label>Parent Item <span class="bb__req">*</span></label>
          <p-autocomplete
            [ngModel]="selectedPartNumber"
            (ngModelChange)="handleItemModelChange($event)"
            [suggestions]="itemSuggestions"
            field="partNumber"
            placeholder="Search part number…"
            [minLength]="1"
            [dropdown]="false"
            (completeMethod)="onItemSearch($event)"
            styleClass="bb__autocomplete"
          >
            <ng-template #item let-item>
              <div class="bb__suggestion">
                <span class="bb__suggestion-pn">{{ item.partNumber }}</span>
                <span class="bb__suggestion-rev">Rev {{ item.revision }}</span>
                <span class="bb__suggestion-desc">{{ item.description }}</span>
              </div>
            </ng-template>
          </p-autocomplete>
        </div>
        <div class="bb__field">
          <label>Description</label>
          <input pInputText [(ngModel)]="newBomDescription" placeholder="Optional" />
        </div>
        <ng-template pTemplate="footer">
          <p-button label="Cancel" severity="secondary" size="small"
                    (onClick)="closeCreateDialog()" />
          <p-button label="Create" severity="primary" size="small"
                    [loading]="creating"
                    [disabled]="!selectedItemId"
                    (onClick)="submitCreate()" />
        </ng-template>
      </p-dialog>

      <p-table [columns]="(gridPreference.activeColumns$ | async) ?? []"
               [value]="items"
               [loading]="loading"
               [paginator]="true"
               [rows]="pageSize"
               [totalRecords]="totalRecords"
               [lazy]="true"
               [lazyLoadOnInit]="false"
               (onLazyLoad)="onLazyLoad($event)"
               [rowHover]="true"
               styleClass="bb__table p-datatable-sm">
        <ng-template pTemplate="header" let-columns>
          <tr>
            @for (col of visibleColumns(columns); track col.key) {
              <th>{{ col.label }}</th>
            }
            <th></th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-item let-columns="columns">
          <tr class="bb__row" (click)="openBoms(item)">
            @for (col of visibleColumns(columns); track col.key) {
              <td [class.bb__pn]="col.key === 'partNumber'">
                @switch (col.key) {
                  @case ('revisionStatus') {
                    <p-tag [value]="revisionStatusLabel(item.revisionStatus)"
                           [severity]="revisionStatusSeverity(item.revisionStatus)" />
                  }
                  @case ('itemRevisionStatus') {
                    <p-tag [value]="revisionStatusLabel(asRevisionStatus(item.itemRevisionStatus))"
                           [severity]="revisionStatusSeverity(asRevisionStatus(item.itemRevisionStatus))" />
                  }
                  @case ('hasDraft') {
                    @if (item.hasDraft) {
                      <p-tag value="Draft pending" severity="warn" />
                    } @else {
                      —
                    }
                  }
                  @default {
                    {{ getCellValue(item, col) ?? '—' }}
                  }
                }
              </td>
            }
            <td class="bb__action">
              <p-button [rounded]="true" [text]="true" size="small"
                        title="View BOMs" aria-label="View BOMs"
                        (click)="$event.stopPropagation(); openBoms(item)">
                <svg lucideView [size]="16" [strokeWidth]="1.75"></svg>
              </p-button>
              @if (item.hasDraft) {
                <p-button [rounded]="true" [text]="true" size="small" severity="warn"
                          title="Edit Draft" aria-label="Edit Draft"
                          (click)="$event.stopPropagation(); openDraft(item)">
                  <svg lucideFilePenLine [size]="16" [strokeWidth]="1.75"></svg>
                </p-button>
              } @else if (item.revisionStatus === 'APPROVED') {
                <p-button [rounded]="true" [text]="true" size="small"
                          title="Create Revision" aria-label="Create Revision"
                          (click)="$event.stopPropagation(); openForRevision(item)">
                  <svg lucideFilePlus [size]="16" [strokeWidth]="1.75"></svg>
                </p-button>
              }
            </td>
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage" let-columns>
          <tr>
            <td [attr.colspan]="visibleColumnCount(columns) + 1" class="bb__empty">
              No BOMs found. Create a BOM from an item master record with Make enabled.
            </td>
          </tr>
        </ng-template>
      </p-table>
    </div>
  `,
  styles: [`
    .bb { padding: 1.5rem; max-width: 1200px; }

    .bb__heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 1rem; }
    .bb__heading-left { display: flex; align-items: baseline; gap: 0.5rem; }
    .bb__title { margin: 0; font-size: 1.375rem; font-weight: 700; color: var(--aurora-text-primary); }
    .bb__count { font-size: 0.875rem; color: var(--p-text-muted-color); }

    .bb__toolbar { display: flex; align-items: center; gap: 0.5rem; margin-bottom: 1rem; }
    .bb__search { width: 320px; }
    .bb__error { margin-bottom: 1rem; }
    .bb__table { width: 100%; }
    .bb__row { cursor: pointer; }
    .bb__pn { font-weight: 600; font-family: monospace; }
    .bb__action { text-align: right; display: flex; gap: 0.25rem; justify-content: flex-end; }
    .bb__empty { text-align: center; padding: 2rem; color: var(--aurora-text-secondary); }

    .bb__field { display: flex; flex-direction: column; gap: 0.25rem; margin-bottom: 0.75rem; }
    .bb__req { color: var(--p-red-500); }
    .bb__dialog-error { margin-bottom: 0.75rem; }
    :host ::ng-deep .bb__autocomplete { width: 100%; }
    :host ::ng-deep .bb__autocomplete .p-autocomplete { width: 100%; }
    :host ::ng-deep .bb__autocomplete .p-autocomplete-input { width: 100%; }
    .bb__suggestion { display: flex; gap: 0.5rem; align-items: baseline; }
    .bb__suggestion-pn { font-weight: 600; font-family: monospace; }
    .bb__suggestion-rev { font-size: 0.8rem; color: var(--p-text-muted-color); }
    .bb__suggestion-desc { font-size: 0.85rem; color: var(--p-text-muted-color); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 180px; }

  `],
})
export class BomBrowserComponent implements OnInit, AfterViewInit {
  private readonly bomApi         = inject(BomApiService);
  private readonly itemApi        = inject(ItemMasterApiService);
  private readonly udfApi         = inject(UdfApiService);
  private readonly router         = inject(Router);
  private readonly destroyRef     = inject(DestroyRef);
  private readonly cdr            = inject(ChangeDetectorRef);
  private readonly messageService = inject(MessageService);
  readonly gridPreference         = inject(GridPreferenceService);

  constructor() {
    inject(BreadcrumbService).set([
      { label: 'Engineering' },
      { label: 'BOMs' },
    ]);
  }

  items: BomSummaryDto[] = [];
  loading = true;
  error = '';
  searchTerm = '';
  pageSize = 20;
  totalRecords = 0;

  // Create dialog state
  showCreateDialog = false;
  selectedPartNumber = '';
  selectedItemId = '';
  itemSuggestions: ItemMasterDto[] = [];
  newBomDescription = '';
  creating = false;
  createError = '';

  private readonly itemSearchSubject = new Subject<string>();
  private searchDebounce?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    this.udfApi.listFields('BOM_BROWSER').pipe(
      map(udfs => udfs.map((u, i): ColumnDef => ({
        key: u.fieldKey,
        label: u.label,
        visible: false,
        order: DEFAULT_BOM_BROWSER_COLUMNS.length + i,
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
    this.itemSearchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap(term => this.itemApi.list({ search: term, size: 20, page: 0 })),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(page => {
      this.itemSuggestions = page.content;
      this.cdr.detectChanges();
    });
  }

  ngAfterViewInit(): void {
    setTimeout(() => this.load(0));
  }

  onLazyLoad(event: TableLazyLoadEvent): void {
    const first = event.first ?? 0;
    const rows = event.rows ?? this.pageSize;
    this.pageSize = rows;
    this.load(Math.floor(first / rows));
  }

  onSearch(): void {
    clearTimeout(this.searchDebounce);
    this.searchDebounce = setTimeout(() => this.load(0), 350);
  }

  onColumnsApplied(columns: ColumnDef[]): void {
    this.gridPreference.apply(columns);
  }

  getCellValue(item: BomSummaryDto, col: ColumnDef): unknown {
    if (!col.udf) return (item as unknown as Record<string, unknown>)[col.key];
    const direct = (item as unknown as Record<string, unknown>)[col.key];
    return direct !== undefined ? direct : item.customFields?.[col.key];
  }

  visibleColumns(columns: ColumnDef[]): ColumnDef[] {
    return columns.filter(c => c.visible).sort((a, b) => a.order - b.order);
  }

  visibleColumnCount(columns: ColumnDef[]): number {
    return this.visibleColumns(columns).length;
  }

  openBoms(item: BomSummaryDto): void {
    this.router.navigate(['/bom', item.parentItemId]);
  }

  openDraft(item: BomSummaryDto): void {
    this.router.navigate(['/boms', item.bomId], { queryParams: { revisionStatus: 'DRAFT' } });
  }

  openForRevision(item: BomSummaryDto): void {
    this.router.navigate(['/boms', item.bomId]);
  }

  openCreateDialog(): void {
    this.resetCreateForm();
    this.showCreateDialog = true;
  }

  closeCreateDialog(): void {
    this.showCreateDialog = false;
  }

  resetCreateForm(): void {
    this.selectedPartNumber = '';
    this.selectedItemId = '';
    this.itemSuggestions = [];
    this.newBomDescription = '';
    this.creating = false;
    this.createError = '';
  }

  onItemSearch(event: AutoCompleteCompleteEvent): void {
    this.itemSearchSubject.next(event.query);
  }

  handleItemModelChange(value: ItemMasterDto | string | null): void {
    if (value && typeof value === 'object') {
      this.selectedPartNumber = value.partNumber;
      this.selectedItemId = value.id;
    } else {
      this.selectedPartNumber = (value as string) ?? '';
      if (!value) this.selectedItemId = '';
    }
    this.cdr.detectChanges();
  }

  submitCreate(): void {
    if (!this.selectedItemId) { return; }
    this.creating = true;
    this.createError = '';
    this.bomApi.create({
      parentItemId: this.selectedItemId,
      description: this.newBomDescription.trim() || undefined,
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: bom => {
        this.creating = false;
        this.showCreateDialog = false;
        this.cdr.detectChanges();
        this.router.navigate(['/boms', bom.id]);
      },
      error: err => {
        this.creating = false;
        this.createError = err.error?.message ?? 'Failed to create BOM. Please try again.';
        this.cdr.detectChanges();
      },
    });
  }

  asRevisionStatus(s: string): RevisionStatus {
    return s as RevisionStatus;
  }

  revisionStatusLabel(s: RevisionStatus): string {
    if (s === 'PENDING_APPROVAL') return 'Pending';
    if (s === 'APPROVED') return 'Approved';
    return 'Draft';
  }

  revisionStatusSeverity(s: RevisionStatus): 'info' | 'warn' | 'success' | 'secondary' {
    if (s === 'APPROVED') return 'success';
    if (s === 'PENDING_APPROVAL') return 'warn';
    return 'secondary';
  }

  private load(page: number): void {
    this.loading = true;
    this.error = '';
    this.bomApi.listHeaders(this.searchTerm || undefined, page, this.pageSize)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => {
          this.items = result.content;
          this.totalRecords = result.totalElements;
          this.loading = false;
          this.cdr.detectChanges();
        },
        error: () => {
          this.error = 'Failed to load BOMs. Please try again.';
          this.loading = false;
          this.cdr.detectChanges();
        },
      });
  }
}
