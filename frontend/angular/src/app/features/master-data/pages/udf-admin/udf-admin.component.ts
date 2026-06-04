import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { InputNumberModule } from 'primeng/inputnumber';
import { TextareaModule } from 'primeng/textarea';
import { MessageModule } from 'primeng/message';
import { ToastModule } from 'primeng/toast';
import { SkeletonModule } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { MessageService } from 'primeng/api';
import { BreadcrumbService } from '../../../../shared/ui';
import {
  UdfAdminApiService,
  UdfFieldDefinition,
  UdfFieldType,
} from '../../services/udf-admin-api.service';

interface ModuleOption { label: string; value: string; }
interface FieldTypeOption { label: string; value: UdfFieldType; }

@Component({
  selector: 'app-udf-admin',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    TableModule, ButtonModule, DialogModule,
    InputTextModule, SelectModule, ToggleSwitchModule,
    InputNumberModule, TextareaModule,
    MessageModule, ToastModule, SkeletonModule,
  ],
  providers: [MessageService],
  template: `
    <div class="ua">
      <p-toast />

      <!-- ── Header ───────────────────────────────────────────────── -->
      <div class="ua__header">
        <div>
          <h2 class="ua__title">User-Defined Fields</h2>
          <p class="ua__subtitle">
            Add custom fields to any module so every record can capture business-specific data.
          </p>
        </div>
        <p-button label="New Field" icon="pi pi-plus" severity="primary" size="small"
                  [disabled]="loading" (onClick)="openCreate()" />
      </div>

      <!-- ── Module tab bar ────────────────────────────────────────── -->
      <div class="ua__module-bar">
        @for (mod of moduleOptions; track mod.value) {
          <button class="ua__mod-tab"
                  [class.ua__mod-tab--active]="selectedModule === mod.value"
                  [disabled]="loading"
                  (click)="selectModule(mod.value)">
            {{ mod.label }}
          </button>
        }
      </div>

      <!-- ── Content ───────────────────────────────────────────────── -->
      @if (loading) {
        <div class="ua__skeleton-col">
          @for (i of [1,2,3,4]; track i) { <p-skeleton height="2.5rem" /> }
        </div>

      } @else if (fields.length === 0) {
        <div class="ua__empty">
          <span class="ua__empty-icon pi pi-sliders-v"></span>
          <p class="ua__empty-title">No fields defined for {{ selectedModuleLabel }}</p>
          <p class="ua__empty-sub">Fields you add here will appear on every {{ selectedModuleLabel }} record.</p>
          <p-button label="Add first field" size="small" severity="secondary"
                    (onClick)="openCreate()" />
        </div>

      } @else {
        <p-table [value]="fields" styleClass="ua__table p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th style="width:50px">#</th>
              <th>Field Key</th>
              <th>Label</th>
              <th style="width:90px">Type</th>
              <th style="width:80px; text-align:center">Required</th>
              <th>Default</th>
              <th>Options</th>
              <th style="width:200px"></th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-field>
            <tr>
              <td class="ua__cell-muted">{{ field.displayOrder }}</td>
              <td><code class="ua__code">{{ field.fieldKey }}</code></td>
              <td>{{ field.label }}</td>
              <td>
                <span [class]="typeBadgeClass(field.fieldType)">{{ field.fieldType }}</span>
              </td>
              <td style="text-align:center">
                @if (field.required) {
                  <span class="ua__check pi pi-check"></span>
                }
              </td>
              <td class="ua__cell-muted">{{ field.defaultValue ?? '—' }}</td>
              <td class="ua__cell-muted">
                @if (field.fieldType === 'LIST') {
                  {{ field.listOptions?.length ?? 0 }} option{{ (field.listOptions?.length ?? 0) !== 1 ? 's' : '' }}
                } @else {
                  —
                }
              </td>
              <td>
                @if (pendingDeleteId === field.id) {
                  <div class="ua__delete-confirm">
                    <span class="ua__confirm-label">Remove?</span>
                    <p-button label="Cancel" severity="secondary" [text]="true" size="small"
                              (onClick)="cancelDelete()" />
                    <p-button label="Delete" severity="danger" size="small"
                              [loading]="deleting" (onClick)="confirmDelete(false)" />
                    <p-button label="Force" severity="danger" [outlined]="true" size="small"
                              [loading]="deleting" (onClick)="confirmDelete(true)"
                              title="Force delete: nullifies this field's value in all existing records" />
                  </div>
                } @else {
                  <p-button icon="pi pi-trash" severity="danger" [text]="true" size="small"
                            title="Delete field" (onClick)="requestDelete(field.id)" />
                }
              </td>
            </tr>
          </ng-template>
        </p-table>
      }

      <!-- ── Create dialog ─────────────────────────────────────────── -->
      <p-dialog header="New Custom Field"
                [(visible)]="showCreateDialog"
                (onHide)="closeCreate()"
                [modal]="true"
                [style]="{ width: '560px' }"
                [draggable]="false">

        @if (serverErrors.length) {
          <div class="ua__dialog-errors">
            @for (err of serverErrors; track err) {
              <p-message severity="error" [text]="err" />
            }
          </div>
        }

        <form [formGroup]="createForm" class="ua__create-form" (ngSubmit)="saveField()">

          <div class="ua__form-field">
            <label class="ua__form-label">
              Field Key <span class="ua__req">*</span>
            </label>
            <input pInputText formControlName="fieldKey"
                   placeholder="e.g. cost_centre" style="width:100%" />
            <small class="ua__form-hint">
              Lowercase letters, digits, underscores. Cannot be changed after creation.
            </small>
            @if (createForm.get('fieldKey')?.errors?.['pattern'] && createForm.get('fieldKey')?.touched) {
              <small class="ua__form-error">Must start with a letter; only a–z, 0–9 and _ allowed</small>
            }
          </div>

          <div class="ua__form-field">
            <label class="ua__form-label">Label <span class="ua__req">*</span></label>
            <input pInputText formControlName="label"
                   placeholder="e.g. Cost Centre" style="width:100%" />
          </div>

          <div class="ua__form-row">
            <div class="ua__form-field">
              <label class="ua__form-label">Field Type <span class="ua__req">*</span></label>
              <p-select formControlName="fieldType"
                        [options]="fieldTypeOptions"
                        optionLabel="label" optionValue="value"
                        placeholder="Select type…" />
            </div>
            <div class="ua__form-field">
              <label class="ua__form-label">Required</label>
              <div class="ua__toggle-row">
                <p-toggleswitch formControlName="required" />
                <span class="ua__form-hint">
                  {{ createForm.get('required')?.value ? 'Yes — field must be filled' : 'No — optional' }}
                </span>
              </div>
            </div>
          </div>

          <div class="ua__form-row">
            <div class="ua__form-field">
              <label class="ua__form-label">Display Order</label>
              <p-inputnumber formControlName="displayOrder" [min]="1" placeholder="Auto" />
            </div>
            @if (selectedFieldType !== 'BOOLEAN') {
              <div class="ua__form-field">
                <label class="ua__form-label">Default Value</label>
                <input pInputText formControlName="defaultValue" placeholder="Optional" />
              </div>
            }
          </div>

          <!-- LIST: options textarea -->
          @if (selectedFieldType === 'LIST') {
            <div class="ua__form-field">
              <label class="ua__form-label">
                List Options <span class="ua__req">*</span>
              </label>
              <textarea pTextarea formControlName="listOptions" rows="5"
                        placeholder="One option per line&#10;e.g.&#10;Option A&#10;Option B"
                        style="width:100%"></textarea>
              <small class="ua__form-hint">Enter one option per line.</small>
            </div>
          }

          <!-- TEXT: max length rule -->
          @if (selectedFieldType === 'TEXT') {
            <div class="ua__form-field ua__form-field--half">
              <label class="ua__form-label">Max Length</label>
              <p-inputnumber formControlName="maxLength" [min]="1" placeholder="No limit" />
            </div>
          }

          <!-- NUMBER: min / max rules -->
          @if (selectedFieldType === 'NUMBER') {
            <div class="ua__form-row">
              <div class="ua__form-field">
                <label class="ua__form-label">Min Value</label>
                <p-inputnumber formControlName="minValue" placeholder="No minimum" />
              </div>
              <div class="ua__form-field">
                <label class="ua__form-label">Max Value</label>
                <p-inputnumber formControlName="maxValue" placeholder="No maximum" />
              </div>
            </div>
          }

        </form>

        <ng-template pTemplate="footer">
          <p-button label="Cancel" severity="secondary" (onClick)="closeCreate()" />
          <p-button label="Create Field" severity="primary"
                    [loading]="saving"
                    [disabled]="createForm.invalid"
                    (onClick)="saveField()" />
        </ng-template>
      </p-dialog>

    </div>
  `,
  styles: [`
    .ua { padding: 1.25rem; }

    /* ── Header ── */
    .ua__header {
      display: flex; align-items: flex-start; justify-content: space-between;
      margin-bottom: 1.25rem;
    }
    .ua__title { margin: 0; font-size: 1.375rem; font-weight: 700; }
    .ua__subtitle { margin: 0.25rem 0 0; font-size: 0.8125rem; color: var(--p-text-muted-color); }

    /* ── Module tab bar ── */
    .ua__module-bar {
      display: flex; flex-wrap: wrap; gap: 0.25rem;
      border-bottom: 1px solid var(--p-surface-border);
      padding-bottom: 0.625rem; margin-bottom: 1.25rem;
    }
    .ua__mod-tab {
      padding: 0.375rem 0.875rem;
      border: 1px solid transparent; border-radius: 6px;
      background: none; cursor: pointer;
      font-size: 0.8125rem; font-weight: 500;
      color: var(--p-text-muted-color);
      transition: background 120ms ease, color 120ms ease, border-color 120ms ease;
      &:hover:not(:disabled) {
        background: var(--p-surface-100);
        color: var(--p-text-color);
        border-color: var(--p-surface-border);
      }
      &--active {
        background: var(--aurora-brand-primary);
        color: #ffffff;
        border-color: var(--aurora-brand-primary);
        &:hover { background: color-mix(in srgb, var(--aurora-brand-primary) 88%, #000); }
      }
      &:disabled { opacity: 0.5; cursor: default; }
    }

    /* ── Skeleton loading ── */
    .ua__skeleton-col { display: flex; flex-direction: column; gap: 0.5rem; }

    /* ── Empty state ── */
    .ua__empty {
      display: flex; flex-direction: column; align-items: center;
      gap: 0.5rem; padding: 3rem 1rem; text-align: center;
      color: var(--p-text-muted-color);
    }
    .ua__empty-icon { font-size: 2rem; opacity: 0.35; margin-bottom: 0.5rem; }
    .ua__empty-title { margin: 0; font-size: 0.9375rem; font-weight: 600; color: var(--p-text-color); }
    .ua__empty-sub   { margin: 0; font-size: 0.8125rem; }

    /* ── Table ── */
    .ua__table { margin-top: 0; }
    .ua__cell-muted { color: var(--p-text-muted-color); font-size: 0.8125rem; }
    .ua__centered { text-align: center; }
    .ua__check { color: #22c55e; font-size: 0.875rem; }

    .ua__code {
      font-family: ui-monospace, 'Cascadia Code', monospace;
      font-size: 0.8rem;
      background: var(--p-surface-100);
      padding: 0.125rem 0.4rem;
      border-radius: 4px;
      border: 1px solid var(--p-surface-border);
    }

    /* ── Type badges ── */
    .ua__type-badge {
      display: inline-block;
      padding: 0.125rem 0.5rem;
      border-radius: 4px;
      font-size: 0.6875rem; font-weight: 700;
      text-transform: uppercase; letter-spacing: 0.04em;
      &--text    { background: #dbeafe; color: #1d4ed8; }
      &--number  { background: #fef3c7; color: #92400e; }
      &--date    { background: #ede9fe; color: #5b21b6; }
      &--boolean { background: #ccfbf1; color: #047857; }
      &--list    { background: #dcfce7; color: #15803d; }
    }

    /* ── Delete confirmation ── */
    .ua__delete-confirm {
      display: flex; align-items: center; gap: 0.375rem; flex-wrap: wrap;
    }
    .ua__confirm-label {
      font-size: 0.75rem; font-weight: 600; color: #dc2626;
      white-space: nowrap;
    }

    /* ── Create dialog form ── */
    .ua__dialog-errors { display: flex; flex-direction: column; gap: 0.25rem; margin-bottom: 1rem; }

    .ua__create-form { display: flex; flex-direction: column; gap: 1rem; padding: 0.25rem 0; }

    .ua__form-field {
      display: flex; flex-direction: column; gap: 0.3rem;
      &--half { max-width: 240px; }
    }
    .ua__form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }

    .ua__form-label { font-size: 0.8125rem; font-weight: 500; color: var(--p-text-muted-color); }
    .ua__req  { color: #EF4444; }
    .ua__form-hint  { font-size: 0.75rem; color: var(--p-text-muted-color); }
    .ua__form-error { font-size: 0.75rem; color: #EF4444; }

    .ua__toggle-row { display: flex; align-items: center; gap: 0.5rem; padding-top: 0.25rem; }
  `],
})
export class UdfAdminComponent implements OnInit {
  private readonly fb            = inject(FormBuilder);
  private readonly api           = inject(UdfAdminApiService);
  private readonly toast         = inject(MessageService);
  private readonly breadcrumbSvc = inject(BreadcrumbService);
  private readonly destroyRef    = inject(DestroyRef);

  selectedModule  = 'ITEM_MASTER';
  fields: UdfFieldDefinition[] = [];
  loading         = false;
  showCreateDialog = false;
  saving          = false;
  serverErrors: string[] = [];
  pendingDeleteId: string | null = null;
  deleting        = false;

  readonly moduleOptions: ModuleOption[] = [
    { label: 'Item Master', value: 'ITEM_MASTER' },
    { label: 'BOM Header',  value: 'BOM_HEADER'  },
    { label: 'BOM Line',    value: 'BOM_LINE'    },
    { label: 'Work Order',  value: 'WORK_ORDER'  },
    { label: 'Routing',     value: 'ROUTING'     },
    { label: 'Receiving',   value: 'RECEIVING'   },
    { label: 'Inventory',   value: 'INVENTORY'   },
  ];

  readonly fieldTypeOptions: FieldTypeOption[] = [
    { label: 'Text',    value: 'TEXT'    },
    { label: 'Number',  value: 'NUMBER'  },
    { label: 'Date',    value: 'DATE'    },
    { label: 'Boolean', value: 'BOOLEAN' },
    { label: 'List',    value: 'LIST'    },
  ];

  createForm = this.fb.group({
    fieldKey:     ['', [Validators.required, Validators.pattern(/^[a-z][a-z0-9_]{0,99}$/)]],
    label:        ['', [Validators.required, Validators.maxLength(255)]],
    fieldType:    [null as UdfFieldType | null, Validators.required],
    required:     [false],
    displayOrder: [null as number | null],
    defaultValue: [''],
    listOptions:  [''],
    maxLength:    [null as number | null],
    minValue:     [null as number | null],
    maxValue:     [null as number | null],
  });

  get selectedModuleLabel(): string {
    return this.moduleOptions.find(m => m.value === this.selectedModule)?.label ?? this.selectedModule;
  }

  get selectedFieldType(): string | null {
    return this.createForm.get('fieldType')?.value ?? null;
  }

  typeBadgeClass(type: string): string {
    return `ua__type-badge ua__type-badge--${type.toLowerCase()}`;
  }

  ngOnInit(): void {
    this.breadcrumbSvc.set([
      { label: 'Master Data' },
      { label: 'User-Defined Fields' },
    ]);
    this.loadFields();
  }

  selectModule(moduleKey: string): void {
    this.selectedModule = moduleKey;
    this.pendingDeleteId = null;
    this.loadFields();
  }

  private loadFields(): void {
    this.loading = true;
    this.api.listFields(this.selectedModule).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: fields => { this.fields = fields; this.loading = false; },
      error: ()     => {
        this.fields  = [];
        this.loading = false;
        this.toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to load field definitions.' });
      },
    });
  }

  openCreate(): void {
    this.createForm.reset({ required: false, displayOrder: null });
    this.serverErrors = [];
    this.showCreateDialog = true;
  }

  closeCreate(): void {
    this.showCreateDialog = false;
  }

  saveField(): void {
    if (this.createForm.invalid) return;

    const v = this.createForm.value;

    // Validate LIST has at least one option
    if (v.fieldType === 'LIST') {
      const opts = (v.listOptions ?? '').split('\n').map((s: string) => s.trim()).filter(Boolean);
      if (opts.length === 0) {
        this.serverErrors = ['List type requires at least one option.'];
        return;
      }
    }

    this.saving = true;
    this.serverErrors = [];

    this.api.createField({
      moduleKey:       this.selectedModule,
      fieldKey:        v.fieldKey!,
      label:           v.label!,
      fieldType:       v.fieldType as UdfFieldType,
      required:        v.required ?? false,
      displayOrder:    v.displayOrder ?? undefined,
      defaultValue:    v.defaultValue || undefined,
      listOptions:     v.fieldType === 'LIST'
        ? (v.listOptions ?? '').split('\n').map((s: string) => s.trim()).filter(Boolean)
        : undefined,
      validationRules: this.buildValidationRules(),
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.saving = false;
        this.showCreateDialog = false;
        this.toast.add({
          severity: 'success',
          summary: 'Field created',
          detail: `"${v.label}" is now available on all ${this.selectedModuleLabel} records.`,
        });
        this.loadFields();
      },
      error: (err: { status: number; error?: { violations?: { field: string; message: string }[] } }) => {
        this.saving = false;
        if (err.status === 403) {
          this.serverErrors = ['Permission denied. The item-master:udf:manage privilege is required. Ensure your account has the SYSTEM_ADMIN role.'];
        } else if (err.status === 422 && err.error?.violations) {
          this.serverErrors = err.error.violations.map(vi => `${vi.field}: ${vi.message}`);
        } else if (err.status === 409) {
          this.serverErrors = [`Field key "${v.fieldKey}" already exists for ${this.selectedModuleLabel}.`];
        } else {
          this.serverErrors = ['Failed to create field. Please try again.'];
        }
      },
    });
  }

  private buildValidationRules(): Record<string, unknown> | undefined {
    const type = this.createForm.value.fieldType;
    const v    = this.createForm.value;
    const rules: Record<string, unknown> = {};

    if (type === 'TEXT' && v.maxLength && v.maxLength > 0) {
      rules['maxLength'] = v.maxLength;
    } else if (type === 'NUMBER') {
      if (v.minValue !== null && v.minValue !== undefined) rules['min'] = v.minValue;
      if (v.maxValue !== null && v.maxValue !== undefined) rules['max'] = v.maxValue;
    }

    return Object.keys(rules).length > 0 ? rules : undefined;
  }

  requestDelete(fieldId: string): void {
    this.pendingDeleteId = fieldId;
  }

  cancelDelete(): void {
    this.pendingDeleteId = null;
  }

  confirmDelete(force: boolean): void {
    if (!this.pendingDeleteId) return;
    this.deleting = true;

    this.api.deleteField(this.pendingDeleteId, force).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.deleting = false;
        this.pendingDeleteId = null;
        this.toast.add({ severity: 'success', summary: 'Deleted', detail: 'Field removed successfully.' });
        this.loadFields();
      },
      error: (err: { status: number }) => {
        this.deleting = false;
        if (err.status === 409) {
          this.toast.add({
            severity: 'warn',
            summary: 'Field in use',
            detail: 'This field has data in existing records. Use Force Delete to remove it and nullify all values.',
            life: 6000,
          });
        } else {
          this.toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to delete field.' });
        }
      },
    });
  }
}
