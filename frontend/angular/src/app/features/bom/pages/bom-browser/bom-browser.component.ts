import { AfterViewInit, ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AsyncPipe } from '@angular/common';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { MessageModule } from 'primeng/message';
import { PopoverModule } from 'primeng/popover';
import { TagModule } from 'primeng/tag';
import { LucideColumnsSettings } from '@lucide/angular';
import { BreadcrumbService, StatusBadgeComponent } from '../../../../shared/ui';
import { GridPreferenceService, ColumnPickerComponent, ColumnDef } from '../../../../shared/grid';
import { BomApiService } from '../../services/bom-api.service';
import { BomSummaryDto } from '../../models/bom.model';
import { Classification } from '../../../item-master/models/item-master.model';
import { ClassificationLabelPipe } from '../../../item-master/pipes/classification-label.pipe';

const DEFAULT_BOM_BROWSER_COLUMNS: ColumnDef[] = [
  { key: 'partNumber',      label: 'Part Number',     visible: true,  order: 0, locked: true },
  { key: 'revision',        label: 'Rev',             visible: true,  order: 1, locked: true },
  { key: 'itemDescription', label: 'Description',     visible: true,  order: 2, locked: true },
  { key: 'classification',  label: 'Classification',  visible: true,  order: 3 },
  { key: 'unitOfMeasure',   label: 'Unit of Measure', visible: true,  order: 4 },
  { key: 'itemStatus',      label: 'Item Status',     visible: true,  order: 5 },
  { key: 'bomRevision',     label: 'BOM Rev',         visible: true,  order: 6 },
  { key: 'bomStatus',       label: 'BOM Status',      visible: true,  order: 7 },
  { key: 'createdBy',       label: 'Created By',      visible: false, order: 8 },
];

@Component({
  selector: 'app-bom-browser',
  standalone: true,
  imports: [
    AsyncPipe, FormsModule,
    TableModule, ButtonModule, InputTextModule, MessageModule, PopoverModule, TagModule,
    ColumnPickerComponent, StatusBadgeComponent, ClassificationLabelPipe,
    LucideColumnsSettings,
  ],
  providers: [
    {
      provide: GridPreferenceService,
      useFactory: () => new GridPreferenceService('BOM_BROWSER', DEFAULT_BOM_BROWSER_COLUMNS),
    },
  ],
  template: `
    <div class="bb">

      <div class="bb__heading">
        <div class="bb__heading-left">
          <h2 class="bb__title">Bills of Materials</h2>
          <span class="bb__count">({{ totalRecords }} BOMs)</span>
        </div>
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
          (reset)="gridPreference.reset(); colPickerPanel.hide()"
        />
      </p-popover>

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
                  @case ('classification') {
                    <p-tag [value]="asClassification(item.classification) | classificationLabel"
                           [severity]="classificationSeverity(asClassification(item.classification))"
                           [class]="classificationClass(asClassification(item.classification))" />
                  }
                  @case ('itemStatus') {
                    <app-status-badge [status]="item.itemStatus" />
                  }
                  @case ('bomStatus') {
                    <app-status-badge [status]="item.bomStatus" />
                  }
                  @default {
                    {{ item[col.key] ?? '—' }}
                  }
                }
              </td>
            }
            <td class="bb__action">
              <p-button label="View BOMs"
                        severity="secondary"
                        size="small"
                        (click)="$event.stopPropagation(); openBoms(item)" />
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

    .bb__heading { display: flex; align-items: baseline; gap: 0.5rem; margin-bottom: 1rem; }
    .bb__heading-left { display: flex; align-items: baseline; gap: 0.5rem; }
    .bb__title { margin: 0; font-size: 1.375rem; font-weight: 700; color: var(--aurora-text-primary); }
    .bb__count { font-size: 0.875rem; color: var(--p-text-muted-color); }

    .bb__toolbar {
      display: flex; align-items: center; gap: 0.5rem; margin-bottom: 1rem;
    }
    .bb__search { width: 320px; }
    .bb__error { margin-bottom: 1rem; }
    .bb__table { width: 100%; }
    .bb__row { cursor: pointer; }
    .bb__pn { font-weight: 600; font-family: monospace; }
    .bb__action { text-align: right; }
    .bb__empty { text-align: center; padding: 2rem; color: var(--aurora-text-secondary); }

    :host ::ng-deep .p-tag.tag-raw-material { background: #0D9488 !important; color: #fff !important; }
    :host ::ng-deep .p-tag.tag-service      { background: #7C3AED !important; color: #fff !important; }
  `],
})
export class BomBrowserComponent implements OnInit, AfterViewInit {
  private readonly bomApi     = inject(BomApiService);
  private readonly router     = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly cdr        = inject(ChangeDetectorRef);
  readonly gridPreference     = inject(GridPreferenceService);

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

  private searchDebounce?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    this.gridPreference.load();
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

  visibleColumns(columns: ColumnDef[]): ColumnDef[] {
    return columns.filter(c => c.visible).sort((a, b) => a.order - b.order);
  }

  visibleColumnCount(columns: ColumnDef[]): number {
    return this.visibleColumns(columns).length;
  }

  openBoms(item: BomSummaryDto): void {
    this.router.navigate(['/bom', item.parentItemId]);
  }

  asClassification(value: string): Classification {
    return value as Classification;
  }

  classificationSeverity(c: Classification): 'info' | 'warn' | 'secondary' | 'success' | 'danger' | undefined {
    switch (c) {
      case 'PURCHASED_PART': return 'info';
      case 'FABRICATED':     return 'warn';
      case 'ASSEMBLY':       return 'success';
      case 'COTS':           return 'secondary';
      default:               return undefined;
    }
  }

  classificationClass(c: Classification): string {
    if (c === 'RAW_MATERIAL') return 'tag-raw-material';
    if (c === 'SERVICE')      return 'tag-service';
    return '';
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
