import { Component, inject, OnInit } from '@angular/core';
import { AsyncPipe, CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { ButtonModule } from 'primeng/button';
import { PopoverModule } from 'primeng/popover';
import { TagModule } from 'primeng/tag';
import { GridPreferenceService, ColumnPickerComponent, ColumnDef } from '../../../../shared/grid';
import { ItemMasterApiService } from '../../services/item-master-api.service';
import { DEFAULT_ITEM_MASTER_COLUMNS } from '../../constants/default-columns';
import { ItemMasterDto, Classification, ItemStatus } from '../../models/item-master.model';

@Component({
  selector: 'app-item-master-list',
  standalone: true,
  imports: [
    CommonModule, AsyncPipe, FormsModule,
    TableModule, InputTextModule, SelectModule, ButtonModule, PopoverModule, TagModule,
    ColumnPickerComponent,
  ],
  providers: [
    {
      provide: GridPreferenceService,
      useFactory: () => new GridPreferenceService('ITEM_MASTER', DEFAULT_ITEM_MASTER_COLUMNS),
    },
  ],
  template: `
    <div class="item-master-list">
      <div class="item-master-list__toolbar">
        <input pInputText type="text" placeholder="Search part number or description..."
               [(ngModel)]="searchTerm" (ngModelChange)="onSearchChange()" />

        <p-select [options]="classificationOptions" [(ngModel)]="selectedClassification"
                  placeholder="Classification" [showClear]="true"
                  (onChange)="reload()" styleClass="filter-select" />

        <p-select [options]="statusOptions" [(ngModel)]="selectedStatus"
                  placeholder="Status" [showClear]="true"
                  (onChange)="reload()" styleClass="filter-select" />

        <p-button label="Clear filters" severity="secondary" size="small" (onClick)="clearFilters()" />

        <p-button icon="pi pi-sliders-h" [rounded]="true" [text]="true"
                  aria-label="Customize columns" (onClick)="colPickerPanel.toggle($event)" />
      </div>

      <p-popover #colPickerPanel>
        <app-column-picker
          [columns]="(gridPreference.activeColumns$ | async) ?? []"
          (applied)="onColumnsApplied($event); colPickerPanel.hide()"
          (reset)="gridPreference.reset(); colPickerPanel.hide()"
        />
      </p-popover>

      <p-table
        [columns]="(gridPreference.activeColumns$ | async) ?? []"
        [value]="rows"
        [lazy]="true"
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
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-item let-columns="columns">
          <tr>
            @for (col of visibleColumns(columns); track col.key) {
              <td>{{ item[col.key] ?? '—' }}</td>
            }
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage" let-columns>
          <tr>
            <td [attr.colspan]="visibleColumnCount(columns)">No items found.</td>
          </tr>
        </ng-template>
      </p-table>
    </div>
  `,
  styles: [`
    .item-master-list { padding: 1rem; }
    .item-master-list__toolbar { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; margin-bottom: 1rem; }
    :host ::ng-deep .filter-select { min-width: 160px; }
  `],
})
export class ItemMasterListComponent implements OnInit {
  private readonly api = inject(ItemMasterApiService);
  readonly gridPreference = inject(GridPreferenceService);

  rows: ItemMasterDto[] = [];
  totalRecords = 0;
  loading = false;
  pageSize = 20;
  currentPage = 0;

  searchTerm = '';
  selectedClassification: Classification | null = null;
  selectedStatus: ItemStatus | null = null;

  readonly classificationOptions = [
    { label: 'Electronic',    value: 'ELECTRONIC' },
    { label: 'Mechanical',    value: 'MECHANICAL' },
    { label: 'Raw Material',  value: 'RAW_MATERIAL' },
    { label: 'Consumable',    value: 'CONSUMABLE' },
    { label: 'Tooling',       value: 'TOOLING' },
    { label: 'Software',      value: 'SOFTWARE' },
    { label: 'Document',      value: 'DOCUMENT' },
  ];

  readonly statusOptions = [
    { label: 'Active',   value: 'ACTIVE' },
    { label: 'Inactive', value: 'INACTIVE' },
    { label: 'Obsolete', value: 'OBSOLETE' },
  ];

  ngOnInit(): void {
    this.gridPreference.load();
  }

  onLazyLoad(event: TableLazyLoadEvent): void {
    this.currentPage = (event.first ?? 0) / (event.rows ?? this.pageSize);
    this.pageSize = event.rows ?? this.pageSize;
    this.fetchItems();
  }

  onSearchChange(): void {
    this.currentPage = 0;
    this.fetchItems();
  }

  reload(): void {
    this.currentPage = 0;
    this.fetchItems();
  }

  clearFilters(): void {
    this.searchTerm = '';
    this.selectedClassification = null;
    this.selectedStatus = null;
    this.reload();
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

  private fetchItems(): void {
    this.loading = true;
    this.api.list({
      page: this.currentPage,
      size: this.pageSize,
      search: this.searchTerm || undefined,
      classification: this.selectedClassification ?? undefined,
      status: this.selectedStatus ?? undefined,
    }).subscribe({
      next: page => {
        this.rows = page.content;
        this.totalRecords = page.totalElements;
        this.loading = false;
      },
      error: () => { this.loading = false; },
    });
  }
}
