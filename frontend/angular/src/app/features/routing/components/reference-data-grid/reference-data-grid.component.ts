import {
  ChangeDetectorRef, Component, DestroyRef, EventEmitter, Input, Output, inject,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { InputTextModule } from 'primeng/inputtext';
import { CheckboxModule } from 'primeng/checkbox';
import { TagModule } from 'primeng/tag';
import { ConfirmationService, MessageService } from 'primeng/api';
import {
  LucideFilePlusCorner, LucideSave, LucideCircleX, LucidePencil,
  LucidePower, LucidePowerOff, LucideTrash2,
} from '@lucide/angular';
import { ReferenceDataApiService } from '../../services/reference-data-api.service';

/** One editable field of a reference-data row. */
export interface RefFieldDef {
  key: string;
  label: string;
  type: 'text' | 'checkbox' | 'select';
  required?: boolean;
  options?: { label: string; value: string }[];
  width?: string;
}

/**
 * Row shape the grid needs to know about. Deliberately NOT an index-signature type so the concrete
 * reference DTOs (WorkCentreDto, RouteTypeDto, …) remain assignable to `RefRow[]`. Dynamic field
 * access happens through {@link asRecord}.
 */
export type RefRow = { id?: string; active?: boolean };

function asRecord(row: RefRow): Record<string, unknown> {
  return row as Record<string, unknown>;
}

/**
 * Standard editable reference-data grid for the routing Settings screen (US10). Rows are added and
 * edited inline: the title-bar "Add" button appends an editable row (save/cancel icons); the row
 * action column exposes edit (inline), activate/deactivate, and delete (confirmed). All writes go
 * through {@link ReferenceDataApiService} generic CRUD keyed by {@link resource}; the parent listens
 * to {@link changed} to reload the list. No column picker — these are fixed 2–4 column lookups.
 */
@Component({
  selector: 'app-reference-data-grid',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TableModule, InputTextModule, CheckboxModule, TagModule,
    LucideFilePlusCorner, LucideSave, LucideCircleX, LucidePencil,
    LucidePower, LucidePowerOff, LucideTrash2,
  ],
  template: `
    <section class="rdg">
      <div class="rdg__head">
        <h3 class="rdg__title">{{ title }}</h3>
        <button type="button" class="rdg__add" (click)="startAdd()"
                [disabled]="adding || editingId !== null" [attr.aria-label]="'Add ' + title" title="Add">
          <svg lucideFilePlusCorner [size]="16" [strokeWidth]="1.75"></svg><span>Add</span>
        </button>
      </div>

      @if (error) { <small class="rdg__error">{{ error }}</small> }

      <p-table [value]="rows" styleClass="p-datatable-sm p-datatable-gridlines">
        <ng-template pTemplate="header">
          <tr>
            @for (f of fields; track f.key) { <th [style.width]="f.width ?? null">{{ f.label }}</th> }
            <th style="width:6rem">Active</th>
            <th style="width:9rem">Actions</th>
          </tr>
        </ng-template>

        <ng-template pTemplate="body" let-row>
          <tr>
            @if (editingId === row.id) {
              @for (f of fields; track f.key) {
                <td><ng-container [ngTemplateOutlet]="editor" [ngTemplateOutletContext]="{ f: f }" /></td>
              }
              <td><p-tag [value]="row.active ? 'Active' : 'Inactive'"
                         [severity]="row.active ? 'success' : 'secondary'" /></td>
              <td class="rdg__actions">
                <button type="button" class="rdg__ic" title="Save row"
                        [disabled]="!canSave() || saving" (click)="save()">
                  <svg lucideSave [size]="16" [strokeWidth]="1.75"></svg>
                </button>
                <button type="button" class="rdg__ic" title="Cancel" (click)="cancel()">
                  <svg lucideCircleX [size]="16" [strokeWidth]="1.75"></svg>
                </button>
              </td>
            } @else {
              @for (f of fields; track f.key) { <td>{{ display(row, f) }}</td> }
              <td><p-tag [value]="row.active ? 'Active' : 'Inactive'"
                         [severity]="row.active ? 'success' : 'secondary'" /></td>
              <td class="rdg__actions">
                <button type="button" class="rdg__ic" title="Edit"
                        [disabled]="adding || editingId !== null" (click)="startEdit(row)">
                  <svg lucidePencil [size]="16" [strokeWidth]="1.75"></svg>
                </button>
                <button type="button" class="rdg__ic" [title]="row.active ? 'Deactivate' : 'Activate'"
                        [disabled]="adding || editingId !== null" (click)="toggleActive(row)">
                  @if (row.active) { <svg lucidePower [size]="16" [strokeWidth]="1.75"></svg> }
                  @else { <svg lucidePowerOff [size]="16" [strokeWidth]="1.75"></svg> }
                </button>
                <button type="button" class="rdg__ic rdg__ic--danger" title="Delete"
                        [disabled]="adding || editingId !== null" (click)="confirmDelete(row)">
                  <svg lucideTrash2 [size]="16" [strokeWidth]="1.75"></svg>
                </button>
              </td>
            }
          </tr>
        </ng-template>

        <ng-template pTemplate="footer">
          @if (adding) {
            <tr class="rdg__addrow">
              @for (f of fields; track f.key) {
                <td><ng-container [ngTemplateOutlet]="editor" [ngTemplateOutletContext]="{ f: f }" /></td>
              }
              <td><p-tag value="Active" severity="success" /></td>
              <td class="rdg__actions">
                <button type="button" class="rdg__ic" title="Save row"
                        [disabled]="!canSave() || saving" (click)="save()">
                  <svg lucideSave [size]="16" [strokeWidth]="1.75"></svg>
                </button>
                <button type="button" class="rdg__ic" title="Cancel entry" (click)="cancel()">
                  <svg lucideCircleX [size]="16" [strokeWidth]="1.75"></svg>
                </button>
              </td>
            </tr>
          }
        </ng-template>

        <ng-template pTemplate="emptymessage">
          @if (!adding) { <tr><td [attr.colspan]="colspan" class="rdg__empty">None defined.</td></tr> }
        </ng-template>
      </p-table>

      @if (hint) { <small class="rdg__hint">{{ hint }}</small> }
    </section>

    <ng-template #editor let-f="f">
      @switch (f.type) {
        @case ('checkbox') { <p-checkbox [binary]="true" [(ngModel)]="draft[f.key]" /> }
        @case ('select') {
          <select class="rdg__input" [(ngModel)]="draft[f.key]">
            <option value="">Select…</option>
            @for (o of f.options ?? []; track o.value) { <option [value]="o.value">{{ o.label }}</option> }
          </select>
        }
        @default {
          <input pInputText class="rdg__input" [(ngModel)]="draft[f.key]" [placeholder]="f.label" />
        }
      }
    </ng-template>
  `,
  styles: [`
    .rdg { margin-bottom: 1.75rem; }
    .rdg__head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 0.5rem; }
    .rdg__title { font-size: 1.05rem; margin: 0; }
    .rdg__add { display: inline-flex; align-items: center; gap: 0.35rem; border: 1px solid var(--p-content-border-color);
      background: transparent; color: var(--p-primary-color); border-radius: 6px; padding: 0.3rem 0.6rem;
      font-size: 0.8125rem; cursor: pointer; }
    .rdg__add:hover:not(:disabled) { background: var(--p-content-hover-background); }
    .rdg__add:disabled { opacity: 0.5; cursor: default; }
    .rdg__error { display: block; color: var(--p-red-500); font-size: 0.8125rem; margin-bottom: 0.4rem; }
    .rdg__actions { display: flex; gap: 0.25rem; }
    .rdg__ic { border: none; background: transparent; cursor: pointer; color: var(--p-text-muted-color);
      display: inline-flex; padding: 0.15rem; line-height: 0; border-radius: 4px; }
    .rdg__ic:hover:not(:disabled) { color: var(--p-primary-color); background: var(--p-content-hover-background); }
    .rdg__ic:disabled { opacity: 0.4; cursor: default; }
    .rdg__ic--danger:hover:not(:disabled) { color: var(--p-red-500); }
    .rdg__input { width: 100%; padding: 0.3rem 0.4rem; border: 1px solid var(--p-inputtext-border-color); border-radius: 4px; }
    .rdg__empty { color: var(--p-text-muted-color); }
    .rdg__hint { display: block; margin-top: 0.35rem; font-size: 0.75rem; color: var(--p-text-muted-color); }
  `],
})
export class ReferenceDataGridComponent {
  private readonly refApi = inject(ReferenceDataApiService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly messageService = inject(MessageService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  @Input({ required: true }) title!: string;
  /** API path segment, e.g. 'work-centres', 'route-types', 'significant-process-types'. */
  @Input({ required: true }) resource!: string;
  @Input() rows: RefRow[] = [];
  @Input({ required: true }) fields: RefFieldDef[] = [];
  @Input() hint?: string;
  @Output() readonly changed = new EventEmitter<void>();

  adding = false;
  editingId: string | null = null;
  draft: Record<string, unknown> = {};
  saving = false;
  error = '';

  get colspan(): number {
    return this.fields.length + 2;
  }

  startAdd(): void {
    this.editingId = null;
    this.error = '';
    const d: Record<string, unknown> = { active: true };
    for (const f of this.fields) { d[f.key] = f.type === 'checkbox' ? false : ''; }
    this.draft = d;
    this.adding = true;
  }

  startEdit(row: RefRow): void {
    this.adding = false;
    this.error = '';
    this.editingId = row.id ?? null;
    this.draft = { ...asRecord(row) };
  }

  cancel(): void {
    this.adding = false;
    this.editingId = null;
    this.draft = {};
    this.error = '';
  }

  canSave(): boolean {
    return this.fields.every(f => !f.required || !this.isBlank(this.draft[f.key]));
  }

  private isBlank(v: unknown): boolean {
    return v === undefined || v === null || (typeof v === 'string' && v.trim() === '');
  }

  save(): void {
    if (!this.canSave() || this.saving) { return; }
    this.saving = true;
    this.error = '';
    const body: RefRow = { ...this.draft };
    const call = this.adding
      ? this.refApi.createRef(this.resource, body)
      : this.refApi.updateRef(this.resource, this.editingId as string, body);
    call.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.saving = false;
        this.cancel();
        this.messageService.add({ severity: 'success', summary: 'Saved' });
        this.changed.emit();
        this.cdr.detectChanges();
      },
      error: err => {
        this.saving = false;
        this.error = err?.error?.error ?? 'Save failed';
        this.cdr.detectChanges();
      },
    });
  }

  toggleActive(row: RefRow): void {
    if (!row.id) { return; }
    this.refApi.updateRef(this.resource, row.id, { ...asRecord(row), active: !row.active })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: () => {
          this.messageService.add({ severity: 'success', summary: row.active ? 'Deactivated' : 'Activated' });
          this.changed.emit();
          this.cdr.detectChanges();
        },
        error: err => {
          this.messageService.add({ severity: 'error', summary: err?.error?.error ?? 'Update failed' });
          this.cdr.detectChanges();
        },
      });
  }

  confirmDelete(row: RefRow): void {
    if (!row.id) { return; }
    const rec = asRecord(row);
    const label = (rec['name'] ?? rec['code'] ?? 'this item') as string;
    this.confirmationService.confirm({
      header: 'Delete',
      message: `Delete "${label}"? This cannot be undone.`,
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger p-button-sm',
      rejectButtonStyleClass: 'p-button-text p-button-sm',
      accept: () => {
        this.refApi.deleteRef(this.resource, row.id as string)
          .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
            next: () => {
              this.messageService.add({ severity: 'success', summary: 'Deleted' });
              this.changed.emit();
              this.cdr.detectChanges();
            },
            error: err => {
              this.messageService.add({ severity: 'error', summary: err?.error?.error ?? 'Delete failed' });
              this.cdr.detectChanges();
            },
          });
      },
    });
  }

  display(row: RefRow, f: RefFieldDef): string {
    const v = asRecord(row)[f.key];
    if (f.type === 'checkbox') { return v ? 'Yes' : 'No'; }
    if (f.type === 'select' && f.options) {
      return f.options.find(o => o.value === v)?.label ?? (v == null || v === '' ? '—' : String(v));
    }
    return v == null || v === '' ? '—' : String(v);
  }
}
