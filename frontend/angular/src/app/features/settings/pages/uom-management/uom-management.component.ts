import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnInit,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { LucidePencil } from '@lucide/angular';
import {
  PlatformApiService,
  UnitOfMeasureDto,
  CreateUomRequest,
  PatchUomRequest,
} from '../../../../shared/platform';

@Component({
  selector: 'app-uom-management',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    DialogModule,
    SelectModule,
    InputTextModule,
    TableModule,
    TagModule,
    LucidePencil,
  ],
  styles: [`
    .uom { padding: 1.25rem; }

    .uom__heading {
      display: flex; align-items: baseline; justify-content: space-between;
      margin-bottom: 1rem;
    }
    .uom__heading-left { display: flex; align-items: baseline; gap: 0.5rem; }
    .uom__title { margin: 0; font-size: 1.375rem; font-weight: 700; }
    .uom__count { font-size: 0.875rem; color: var(--p-text-muted-color); }

    .uom__toolbar {
      display: flex; align-items: center; gap: 0.5rem;
      flex-wrap: wrap; margin-bottom: 0.75rem;
    }
    :host ::ng-deep .uom__toolbar .p-inputtext { min-width: 220px; }
    :host ::ng-deep .filter-select { min-width: 160px; }
  `],
  template: `
    <div class="uom">

      <!-- Heading row -->
      <div class="uom__heading">
        <div class="uom__heading-left">
          <h2 class="uom__title">Units of Measure</h2>
          <span class="uom__count">({{ filtered.length }} units)</span>
        </div>
        <p-button label="New Unit" icon="pi pi-plus" severity="primary"
                  size="small" (onClick)="openCreate()" />
      </div>

      <!-- Toolbar -->
      <div class="uom__toolbar">
        <input pInputText type="text" placeholder="Search code or name…"
               [(ngModel)]="searchTerm" (ngModelChange)="applyFilters()" />

        <p-select [options]="categoryOptions" [(ngModel)]="selectedCategory"
                  placeholder="Category" [showClear]="true"
                  (onChange)="applyFilters()" styleClass="filter-select" />

        <p-select [options]="activeOptions" [(ngModel)]="includeInactive"
                  (onChange)="load()" styleClass="filter-select" />

        <p-button label="Clear" severity="secondary" size="small" (onClick)="clearFilters()" />
      </div>

      <!-- Table -->
      <p-table [value]="filtered" [loading]="loading" [paginator]="true" [rows]="25"
               [rowsPerPageOptions]="[25, 50, 100]" styleClass="p-datatable-sm">
        <ng-template pTemplate="header">
          <tr>
            <th>Code</th>
            <th>ISO Code</th>
            <th>Name</th>
            <th>Category</th>
            <th>Status</th>
            <th style="width:60px"></th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-uom>
          <tr>
            <td><code>{{ uom.code }}</code></td>
            <td><code>{{ uom.isoCode ?? '—' }}</code></td>
            <td>{{ uom.name }}</td>
            <td>{{ uom.category }}</td>
            <td>
              <p-tag [value]="uom.active ? 'Active' : 'Inactive'"
                     [severity]="uom.active ? 'success' : 'secondary'" />
            </td>
            <td>
              <p-button [text]="true" size="small" (onClick)="openEdit(uom)">
                <svg lucidePencil [size]="15" [strokeWidth]="1.75"></svg>
              </p-button>
            </td>
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage">
          <tr><td colspan="6" class="text-center p-4" style="color:var(--p-text-muted-color)">No units found.</td></tr>
        </ng-template>
      </p-table>
    </div>

    <!-- Create dialog -->
    <p-dialog [(visible)]="showCreate" header="New Unit of Measure"
              [modal]="true" [style]="{ width: '480px' }">
      <div class="flex flex-column gap-3 pt-2">
        <div class="flex flex-column gap-1">
          <label class="font-medium text-sm">Code *</label>
          <input pInputText [(ngModel)]="createForm.code" maxlength="20" />
        </div>
        <div class="flex flex-column gap-1">
          <label class="font-medium text-sm">ISO Code</label>
          <input pInputText [(ngModel)]="createForm.isoCode" maxlength="20" />
        </div>
        <div class="flex flex-column gap-1">
          <label class="font-medium text-sm">Name *</label>
          <input pInputText [(ngModel)]="createForm.name" maxlength="200" />
        </div>
        <div class="flex flex-column gap-1">
          <label class="font-medium text-sm">Category *</label>
          <p-select [options]="categoryOptions" [(ngModel)]="createForm.category"
                      [editable]="true" placeholder="Select or type…" class="w-full">
          </p-select>
        </div>
        @if (createError) {
          <p class="text-red-500 text-sm m-0">{{ createError }}</p>
        }
      </div>
      <ng-template pTemplate="footer">
        <button pButton type="button" label="Cancel" class="p-button-text"
                (click)="showCreate = false"></button>
        <button pButton type="button" label="Create" [disabled]="saving || !canCreate()"
                (click)="submitCreate()"></button>
      </ng-template>
    </p-dialog>

    <!-- Edit dialog -->
    <p-dialog [(visible)]="showEdit" header="Edit Unit of Measure"
              [modal]="true" [style]="{ width: '480px' }">
      <div class="flex flex-column gap-3 pt-2">
        <div class="flex flex-column gap-1">
          <label class="font-medium text-sm">Code</label>
          <input pInputText [value]="editTarget?.code" [disabled]="true" />
        </div>
        <div class="flex flex-column gap-1">
          <label class="font-medium text-sm">ISO Code</label>
          <input pInputText [(ngModel)]="editForm.isoCode" maxlength="20" />
        </div>
        <div class="flex flex-column gap-1">
          <label class="font-medium text-sm">Name</label>
          <input pInputText [(ngModel)]="editForm.name" maxlength="200" />
        </div>
        <div class="flex align-items-center gap-2">
          <input type="checkbox" id="activeChk" [(ngModel)]="editActive" />
          <label for="activeChk" class="font-medium text-sm">Active</label>
        </div>
        @if (editError) {
          <p class="text-red-500 text-sm m-0">{{ editError }}</p>
        }
      </div>
      <ng-template pTemplate="footer">
        <button pButton type="button" label="Cancel" class="p-button-text"
                (click)="showEdit = false"></button>
        <button pButton type="button" label="Save" [disabled]="saving"
                (click)="submitEdit()"></button>
      </ng-template>
    </p-dialog>
  `,
})
export class UomManagementComponent implements OnInit {
  private readonly api = inject(PlatformApiService);
  private readonly cdr = inject(ChangeDetectorRef);

  readonly LucidePencil = LucidePencil;
  readonly activeOptions = [
    { label: 'Active only',       value: false },
    { label: 'Include inactive',  value: true  },
  ];

  loading        = false;
  saving         = false;
  searchTerm     = '';
  selectedCategory: string | null = null;
  includeInactive = false;

  items:    UnitOfMeasureDto[] = [];
  filtered: UnitOfMeasureDto[] = [];
  categoryOptions: string[]    = [];

  showCreate = false;
  createError = '';
  createForm: CreateUomRequest = { code: '', isoCode: '', name: '', category: '' };

  showEdit   = false;
  editError  = '';
  editTarget: UnitOfMeasureDto | null = null;
  editForm: PatchUomRequest = {};
  editActive = true;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.api.listUom(this.includeInactive).subscribe({
      next: data => {
        this.items = data;
        this.categoryOptions = [...new Set(data.map(u => u.category))].sort();
        this.applyFilters();
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }

  applyFilters(): void {
    const term = this.searchTerm.toLowerCase();
    this.filtered = this.items.filter(u => {
      const matchSearch = !term ||
        u.code.toLowerCase().includes(term) ||
        u.name.toLowerCase().includes(term);
      const matchCategory = !this.selectedCategory || u.category === this.selectedCategory;
      return matchSearch && matchCategory;
    });
  }

  clearFilters(): void {
    this.searchTerm      = '';
    this.selectedCategory = null;
    this.includeInactive  = false;
    this.load();
  }

  openCreate(): void {
    this.createForm = { code: '', isoCode: '', name: '', category: '' };
    this.createError = '';
    this.showCreate = true;
  }

  canCreate(): boolean {
    return !!(this.createForm.code?.trim() &&
              this.createForm.name?.trim() &&
              this.createForm.category?.trim());
  }

  submitCreate(): void {
    this.saving = true;
    this.createError = '';
    const req: CreateUomRequest = {
      code: this.createForm.code.trim(),
      isoCode: this.createForm.isoCode?.trim() || undefined,
      name: this.createForm.name.trim(),
      category: this.createForm.category.trim(),
    };
    this.api.createUom(req).subscribe({
      next: () => {
        this.showCreate = false;
        this.saving = false;
        this.load();
        this.cdr.detectChanges();
      },
      error: err => {
        this.createError = err?.error?.message ?? 'Failed to create unit of measure.';
        this.saving = false;
        this.cdr.detectChanges();
      },
    });
  }

  openEdit(uom: UnitOfMeasureDto): void {
    this.editTarget = uom;
    this.editForm   = { isoCode: uom.isoCode ?? '', name: uom.name };
    this.editActive = uom.active;
    this.editError  = '';
    this.showEdit   = true;
  }

  submitEdit(): void {
    if (!this.editTarget) { return; }
    this.saving = true;
    this.editError = '';
    const req: PatchUomRequest = {
      isoCode: this.editForm.isoCode || undefined,
      name:    this.editForm.name    || undefined,
      active:  this.editActive,
    };
    this.api.patchUom(this.editTarget.code, req).subscribe({
      next: () => {
        this.showEdit = false;
        this.saving = false;
        this.load();
        this.cdr.detectChanges();
      },
      error: err => {
        this.editError = err?.error?.message ?? 'Failed to update unit of measure.';
        this.saving = false;
        this.cdr.detectChanges();
      },
    });
  }
}
