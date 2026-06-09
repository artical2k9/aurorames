import {
  AfterViewInit, ChangeDetectorRef, Component, DestroyRef, inject, OnInit, ViewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, debounceTime, distinctUntilChanged, filter, catchError, map, of } from 'rxjs';
import { AsyncPipe, CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { ButtonModule } from 'primeng/button';
import { PopoverModule } from 'primeng/popover';
import { TagModule } from 'primeng/tag';
import { MenuModule } from 'primeng/menu';
import { ToastModule } from 'primeng/toast';
import { Menu } from 'primeng/menu';
import { MenuItem, MessageService } from 'primeng/api';
import { LucideColumnsSettings } from '@lucide/angular';
import { GridPreferenceService, ColumnPickerComponent, ColumnDef } from '../../../../shared/grid';
import { StatusBadgeComponent, BreadcrumbService } from '../../../../shared/ui';
import { UdfApiService } from '../../../../shared/udf/udf-api.service';
import { ItemMasterApiService } from '../../services/item-master-api.service';
import { ClassificationLabelPipe } from '../../pipes/classification-label.pipe';
import { DEFAULT_ITEM_MASTER_COLUMNS } from '../../constants/default-columns';
import {
  ItemMasterDto, Classification, ItemStatus, MakeBuyCode,
} from '../../models/item-master.model';

@Component({
  selector: 'app-item-master-list',
  standalone: true,
  imports: [
    CommonModule, AsyncPipe, FormsModule,
    TableModule, InputTextModule, SelectModule,
    ButtonModule, PopoverModule, TagModule, MenuModule, ToastModule,
    ColumnPickerComponent, StatusBadgeComponent,
    ClassificationLabelPipe, LucideColumnsSettings,
  ],
  providers: [
    MessageService,
    {
      provide: GridPreferenceService,
      useFactory: () => new GridPreferenceService('ITEM_MASTER', DEFAULT_ITEM_MASTER_COLUMNS),
    },
  ],
  template: `
    <p-toast />

    <div class="iml">

      <!-- Heading row -->
      <div class="iml__heading">
        <div class="iml__heading-left">
          <h2 class="iml__title">Item Master</h2>
          <span class="iml__count">({{ totalRecords }} items)</span>
        </div>
        <p-button label="New Item" icon="pi pi-plus" severity="primary"
                  size="small" (onClick)="navigateToCreate()" />
      </div>

      <!-- Toolbar -->
      <div class="iml__toolbar">
        <input pInputText type="text" placeholder="Search part number or description..."
               [(ngModel)]="searchTerm" (ngModelChange)="onSearchChange()" />

        <p-select [options]="classificationOptions" [(ngModel)]="selectedClassification"
                  optionLabel="label" optionValue="value"
                  placeholder="Classification" [showClear]="true"
                  (onChange)="reload()" styleClass="filter-select" />

        <p-select [options]="statusOptions" [(ngModel)]="selectedStatus"
                  optionLabel="label" optionValue="value"
                  placeholder="Status" [showClear]="true"
                  (onChange)="reload()" styleClass="filter-select" />

        <p-select [options]="makeBuyOptions" [(ngModel)]="selectedMakeBuy"
                  optionLabel="label" optionValue="value"
                  placeholder="Make / Buy" [showClear]="true"
                  (onChange)="reload()" styleClass="filter-select" />

        <p-button label="Clear" severity="secondary" size="small" (onClick)="clearFilters()" />

        <p-button [rounded]="true" [text]="true"
                  aria-label="Customise columns" (onClick)="colPickerPanel.toggle($event)">
          <svg lucideColumnsSettings [size]="16" [strokeWidth]="1.75"></svg>
        </p-button>
      </div>

      <!-- Selection action bar -->
      @if (selectedItems.length > 0) {
        <div class="iml__selection-bar">
          <span>{{ selectedItems.length }} item{{ selectedItems.length > 1 ? 's' : '' }} selected</span>
          <p-button label="View Detail" severity="secondary" size="small"
                    [disabled]="selectedItems.length !== 1"
                    (onClick)="navigateToDetail(selectedItems[0].id)" />
          <p-button label="Edit" severity="secondary" size="small"
                    [disabled]="selectedItems.length !== 1"
                    (onClick)="navigateToEdit(selectedItems[0].id)" />
          <p-button label="Clone Item" severity="secondary" size="small"
                    [disabled]="selectedItems.length !== 1"
                    (onClick)="cloneSelected()" />
          <p-button label="Obsolete" severity="danger" size="small"
                    [loading]="obsoleting" (onClick)="obsoleteSelected()" />
          <p-button label="Clear" [text]="true" size="small" (onClick)="selectedItems = []" />
        </div>
      }

      <p-popover #colPickerPanel>
        <app-column-picker
          [columns]="(gridPreference.activeColumns$ | async) ?? []"
          (applied)="onColumnsApplied($event); colPickerPanel.hide()"
          (cancelled)="colPickerPanel.hide()"
          (reset)="gridPreference.reset(); colPickerPanel.hide()"
        />
      </p-popover>

      <!-- Row overflow menu (shared) -->
      <p-menu #rowMenu [model]="rowMenuItems" [popup]="true" appendTo="body" />

      <p-table
        [columns]="(gridPreference.activeColumns$ | async) ?? []"
        [value]="rows"
        [(selection)]="selectedItems"
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
            <th style="width:3rem">
              <p-tableHeaderCheckbox />
            </th>
            @for (col of visibleColumns(columns); track col.key) {
              <th>{{ col.label }}</th>
            }
            <th style="width:9rem">Actions</th>
          </tr>
        </ng-template>

        <ng-template pTemplate="body" let-item let-columns="columns">
          <tr>
            <td>
              <p-tableCheckbox [value]="item" />
            </td>
            @for (col of visibleColumns(columns); track col.key) {
              <td>
                @switch (col.key) {
                  @case ('classification') {
                    <p-tag [value]="item.classification | classificationLabel"
                           [severity]="classificationSeverity(item.classification)"
                           [class]="classificationClass(item.classification)" />
                  }
                  @case ('status') {
                    <app-status-badge [status]="item.status" />
                  }
                  @default {
                    {{ getCellValue(item, col) ?? '—' }}
                  }
                }
              </td>
            }
            <td>
              <div class="iml__actions">
                <p-button label="View" [text]="true" size="small"
                          (onClick)="navigateToDetail(item.id)" />
                <p-button label="Edit" [text]="true" size="small"
                          (onClick)="navigateToEdit(item.id)" />
                <p-button icon="pi pi-ellipsis-v" [text]="true" size="small"
                          (onClick)="showRowMenu($event, item)" />
              </div>
            </td>
          </tr>
        </ng-template>

        <ng-template pTemplate="emptymessage" let-columns>
          <tr>
            <td [attr.colspan]="visibleColumnCount(columns) + 2">No items found.</td>
          </tr>
        </ng-template>

        <ng-template pTemplate="paginatorleft">
          @if (totalRecords > 0) {
            <span class="iml__pagination-text">
              Showing {{ paginationStart }}–{{ paginationEnd }} of {{ totalRecords }} items
            </span>
          }
        </ng-template>
      </p-table>

    </div>
  `,
  styles: [`
    .iml { padding: 1.25rem; }

    .iml__heading {
      display: flex; align-items: baseline; justify-content: space-between;
      margin-bottom: 1rem;
    }
    .iml__heading-left { display: flex; align-items: baseline; gap: 0.5rem; }
    .iml__title { margin: 0; font-size: 1.375rem; font-weight: 700; }
    .iml__count { font-size: 0.875rem; color: var(--p-text-muted-color); }

    .iml__toolbar {
      display: flex; align-items: center; gap: 0.5rem;
      flex-wrap: wrap; margin-bottom: 0.75rem;
    }
    :host ::ng-deep .filter-select { min-width: 150px; }
    :host ::ng-deep .iml__makebuy .p-selectbutton { font-size: 0.8125rem; }

    .iml__selection-bar {
      display: flex; align-items: center; gap: 0.75rem;
      padding: 0.5rem 0.75rem; margin-bottom: 0.5rem;
      background: var(--p-surface-100);
      border: 1px solid var(--p-surface-border);
      border-radius: 6px; font-size: 0.875rem;
    }

    .iml__actions { display: flex; align-items: center; gap: 0.125rem; }

    .iml__pagination-text { font-size: 0.8125rem; color: var(--p-text-muted-color); }

    /* Teal chip for RAW_MATERIAL */
    :host ::ng-deep .p-tag.tag-raw-material {
      background: #0D9488 !important;
      color: #fff !important;
    }
    :host ::ng-deep .p-tag.tag-service {
      background: #7C3AED !important;
      color: #fff !important;
    }
  `],
})
export class ItemMasterListComponent implements OnInit, AfterViewInit {
  private readonly api            = inject(ItemMasterApiService);
  private readonly router         = inject(Router);
  private readonly messageService = inject(MessageService);
  private readonly destroyRef     = inject(DestroyRef);
  private readonly cdr            = inject(ChangeDetectorRef);
  private readonly udfApi         = inject(UdfApiService);
  readonly gridPreference         = inject(GridPreferenceService);
  private readonly breadcrumbSvc  = inject(BreadcrumbService);

  @ViewChild('rowMenu') rowMenu!: Menu;

  rows: ItemMasterDto[] = [];
  totalRecords = 0;
  loading = true;
  obsoleting = false;
  pageSize = 20;
  currentPage = 0;

  selectedItems: ItemMasterDto[] = [];
  rowMenuItems: MenuItem[] = [];

  searchTerm = '';
  selectedClassification: Classification | null = null;
  selectedStatus: ItemStatus | null = null;
  selectedMakeBuy: MakeBuyCode | null = null;

  private readonly searchSubject = new Subject<string>();

  readonly classificationOptions = [
    { label: 'Assembly',       value: 'ASSEMBLY' },
    { label: 'COTS',           value: 'COTS' },
    { label: 'Fabricated',     value: 'FABRICATED' },
    { label: 'Purchased Part', value: 'PURCHASED_PART' },
    { label: 'Raw Material',   value: 'RAW_MATERIAL' },
    { label: 'Service',        value: 'SERVICE' },
  ];

  readonly statusOptions = [
    { label: 'Active',   value: 'ACTIVE' },
    { label: 'Obsolete', value: 'OBSOLETE' },
  ];

  readonly makeBuyOptions = [
    { label: 'Make',   value: 'MAKE' },
    { label: 'Buy',    value: 'BUY' },
    { label: 'Either', value: 'EITHER' },
  ];

  get paginationStart(): number {
    return this.currentPage * this.pageSize + 1;
  }

  get paginationEnd(): number {
    return Math.min((this.currentPage + 1) * this.pageSize, this.totalRecords);
  }

  ngOnInit(): void {
    this.breadcrumbSvc.set([
      { label: 'Master Data' },
      { label: 'Item Master' },
    ]);
    this.udfApi.listFields('ITEM_MASTER').pipe(
      map(udfs => udfs.map((u, i): ColumnDef => ({
        key: u.fieldKey,
        label: u.label,
        visible: false,
        order: DEFAULT_ITEM_MASTER_COLUMNS.length + i,
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
      filter(term => term.length === 0 || term.length >= 3),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => {
      this.currentPage = 0;
      this.fetchItems();
    });
  }

  ngAfterViewInit(): void {
    setTimeout(() => this.fetchItems());
  }

  onLazyLoad(event: TableLazyLoadEvent): void {
    this.currentPage = (event.first ?? 0) / (event.rows ?? this.pageSize);
    this.pageSize = event.rows ?? this.pageSize;
    this.fetchItems();
  }

  onSearchChange(): void {
    this.searchSubject.next(this.searchTerm);
  }

  reload(): void {
    this.currentPage = 0;
    this.fetchItems();
  }

  clearFilters(): void {
    this.searchTerm = '';
    this.selectedClassification = null;
    this.selectedStatus = null;
    this.selectedMakeBuy = null;
    this.reload();
  }

  onColumnsApplied(columns: ColumnDef[]): void {
    this.gridPreference.apply(columns);
  }

  getCellValue(item: ItemMasterDto, col: ColumnDef): unknown {
    if (!col.udf) return (item as Record<string, unknown>)[col.key];
    const direct = (item as Record<string, unknown>)[col.key];
    return direct !== undefined ? direct : item.customFields?.[col.key];
  }

  visibleColumns(columns: ColumnDef[]): ColumnDef[] {
    return columns.filter(c => c.visible).sort((a, b) => a.order - b.order);
  }

  visibleColumnCount(_columns: ColumnDef[]): number {
    return this.visibleColumns(_columns).length;
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

  navigateToCreate(): void {
    this.router.navigate(['/item-master/new']);
  }

  navigateToDetail(id: string): void {
    this.router.navigate(['/item-master', id]);
  }

  navigateToEdit(id: string): void {
    this.router.navigate(['/item-master', id, 'edit']);
  }

  cloneSelected(): void {
    if (this.selectedItems.length === 1) {
      this.router.navigate(['/item-master/new'], { queryParams: { cloneFrom: this.selectedItems[0].id } });
    }
  }

  showRowMenu(event: Event, item: ItemMasterDto): void {
    this.rowMenuItems = [
      {
        label: 'View Detail',
        icon: 'pi pi-eye',
        command: () => this.navigateToDetail(item.id),
      },
      {
        label: 'Edit',
        icon: 'pi pi-pencil',
        command: () => this.navigateToEdit(item.id),
      },
      {
        label: 'Clone Item',
        icon: 'pi pi-copy',
        command: () => this.router.navigate(['/item-master/new'], { queryParams: { cloneFrom: item.id } }),
      },
      {
        label: 'BOMs',
        icon: 'pi pi-sitemap',
        command: () => this.router.navigate(['/item-master', item.id, 'boms']),
      },
      {
        label: 'Obsolete',
        icon: 'pi pi-ban',
        command: () => this.obsoleteItem(item.id),
      },
    ];
    this.rowMenu.toggle(event);
  }

  obsoleteItem(id: string): void {
    this.api.obsolete(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Item obsoleted' });
        this.reload();
      },
    });
  }

  obsoleteSelected(): void {
    this.obsoleting = true;
    const ids = this.selectedItems.map(i => i.id);
    let done = 0;
    ids.forEach(id => {
      this.api.obsolete(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: () => {
          done++;
          if (done === ids.length) {
            this.obsoleting = false;
            this.selectedItems = [];
            this.messageService.add({ severity: 'success', summary: `${ids.length} item(s) obsoleted` });
            this.reload();
          }
        },
        error: () => {
          done++;
          if (done === ids.length) {
            this.obsoleting = false;
            this.reload();
          }
        },
      });
    });
  }

  private fetchItems(): void {
    this.loading = true;
    this.api.list({
      page: this.currentPage,
      size: this.pageSize,
      search: this.searchTerm || undefined,
      classification: this.selectedClassification ?? undefined,
      status: this.selectedStatus ?? undefined,
      makeBuyCode: this.selectedMakeBuy ?? undefined,
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: page => {
        this.rows = page.content;
        this.totalRecords = page.totalElements;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }
}
