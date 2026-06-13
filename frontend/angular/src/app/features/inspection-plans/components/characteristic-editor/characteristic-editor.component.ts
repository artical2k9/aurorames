import {
  ChangeDetectorRef, Component, DestroyRef, inject, Input, OnChanges, SimpleChanges,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { SelectModule } from 'primeng/select';
import { CheckboxModule } from 'primeng/checkbox';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { InspectionPlanApiService } from '../../services/inspection-plan-api.service';
import {
  CharacteristicDto, CharacteristicType, SampleSizeRule,
} from '../../models/inspection-plan.model';

@Component({
  selector: 'app-characteristic-editor',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TableModule, ButtonModule, DialogModule,
    InputTextModule, InputNumberModule, SelectModule, CheckboxModule, TagModule, ToastModule,
  ],
  providers: [MessageService],
  template: `
    <p-toast />
    <div class="che">
      <div class="che__bar">
        <h3 class="che__title">Characteristics ({{ rows.length }})</h3>
        @if (editable) {
          <p-button label="Add Characteristic" icon="pi pi-plus" size="small"
                    (onClick)="openAdd()" />
        }
      </div>

      <p-table [value]="rows" dataKey="id" styleClass="p-datatable-gridlines p-datatable-sm">
        <ng-template pTemplate="header">
          <tr>
            <th style="width:5rem">#</th>
            <th>Name</th>
            <th>Type</th>
            <th>Spec</th>
            <th>Sample</th>
            @if (editable) { <th style="width:8rem">Actions</th> }
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-c>
          <tr>
            <td>{{ c.characteristicNumber }}</td>
            <td>{{ c.name }}</td>
            <td><p-tag [value]="c.characteristicType" severity="info" /></td>
            <td>{{ specSummary(c) }}</td>
            <td>{{ c.sampleSizeRule === 'FIXED_COUNT' ? ('n=' + c.sampleSizeCount) : 'All' }}</td>
            @if (editable) {
              <td>
                <p-button [rounded]="true" [text]="true" size="small" icon="pi pi-pencil"
                          title="Edit" (onClick)="openEdit(c)" />
                <p-button [rounded]="true" [text]="true" size="small" icon="pi pi-trash"
                          severity="danger" title="Delete" (onClick)="remove(c)" />
              </td>
            }
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage">
          <tr><td [attr.colspan]="editable ? 6 : 5">No characteristics defined.</td></tr>
        </ng-template>
      </p-table>

      <p-dialog [header]="editing ? 'Edit Characteristic' : 'Add Characteristic'"
                [(visible)]="showDialog" [modal]="true" [style]="{ width: '520px' }">
        <div class="che__form">
          <label>Number *</label>
          <p-inputNumber [(ngModel)]="draft.characteristicNumber" [min]="1" [useGrouping]="false" />
          <label>Name *</label>
          <input pInputText [(ngModel)]="draft.name" />
          <label>Source *</label>
          <p-select [options]="sourceOptions" [(ngModel)]="draft.source"
                    optionLabel="label" optionValue="value" />
          <label>Type *</label>
          <p-select [options]="typeOptions" [(ngModel)]="draft.characteristicType"
                    optionLabel="label" optionValue="value" (onChange)="onTypeChange()" />

          @if (draft.characteristicType === 'SPECIFIC') {
            <label>Nominal / Lower / Upper</label>
            <div class="che__row">
              <p-inputNumber [(ngModel)]="draft.nominalValue" placeholder="Nominal" />
              <p-inputNumber [(ngModel)]="draft.lowerLimit" placeholder="Lower" />
              <p-inputNumber [(ngModel)]="draft.upperLimit" placeholder="Upper" />
            </div>
          }
          @if (draft.characteristicType === 'COMMON') {
            <div class="che__check">
              <p-checkbox [(ngModel)]="draft.expectedBoolean" [binary]="true" inputId="eb" />
              <label for="eb">Expected answer is "conforms" (true)</label>
            </div>
          }
          @if (draft.characteristicType === 'CALCULATED') {
            <label>Expression *</label>
            <input pInputText [(ngModel)]="draft.expression" placeholder="(C10 + C20) / 2" />
          }

          <label>Sample Size Rule *</label>
          <p-select [options]="sampleOptions" [(ngModel)]="draft.sampleSizeRule"
                    optionLabel="label" optionValue="value" />
          @if (draft.sampleSizeRule === 'FIXED_COUNT') {
            <label>Sample Count *</label>
            <p-inputNumber [(ngModel)]="draft.sampleSizeCount" [min]="1" [useGrouping]="false" />
          }

          @if (serverError) { <small class="che__error">{{ serverError }}</small> }
        </div>
        <ng-template pTemplate="footer">
          <p-button label="Cancel" severity="secondary" size="small" (onClick)="showDialog = false" />
          <p-button label="Save" severity="primary" size="small"
                    [loading]="saving" [disabled]="!canSave()" (onClick)="save()" />
        </ng-template>
      </p-dialog>
    </div>
  `,
  styles: [`
    .che { margin-top: 1rem; }
    .che__bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 0.5rem; }
    .che__title { margin: 0; font-size: 1.05rem; font-weight: 600; }
    .che__form { display: flex; flex-direction: column; gap: 0.375rem; }
    .che__form label { font-size: 0.8125rem; font-weight: 600; margin-top: 0.375rem; }
    .che__row { display: flex; gap: 0.5rem; }
    .che__check { display: flex; align-items: center; gap: 0.5rem; margin-top: 0.5rem; }
    .che__error { color: var(--p-red-500); }
  `],
})
export class CharacteristicEditorComponent implements OnChanges {
  private readonly api            = inject(InspectionPlanApiService);
  private readonly messageService = inject(MessageService);
  private readonly destroyRef     = inject(DestroyRef);
  private readonly cdr            = inject(ChangeDetectorRef);

  @Input() planId!: string;
  @Input() revisionNumber?: number;
  @Input() editable = false;

  rows: CharacteristicDto[] = [];

  showDialog = false;
  editing = false;
  saving = false;
  serverError = '';
  draft: Partial<CharacteristicDto> = {};
  private editId: string | null = null;

  readonly sourceOptions = [
    { label: 'Design', value: 'DESIGN' },
    { label: 'In Process', value: 'IN_PROCESS' },
  ];
  readonly typeOptions = [
    { label: 'Specific (numeric)', value: 'SPECIFIC' },
    { label: 'Common (boolean)', value: 'COMMON' },
    { label: 'Calculated', value: 'CALCULATED' },
  ];
  readonly sampleOptions = [
    { label: 'All', value: 'ALL' },
    { label: 'Fixed count', value: 'FIXED_COUNT' },
  ];

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['planId'] || changes['revisionNumber']) && this.planId) {
      this.load();
    }
  }

  load(): void {
    this.api.listCharacteristics(this.planId, this.revisionNumber)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: chars => { this.rows = chars; this.cdr.detectChanges(); },
        error: () => { this.rows = []; this.cdr.detectChanges(); },
      });
  }

  specSummary(c: CharacteristicDto): string {
    if (c.characteristicType === 'SPECIFIC') {
      return [c.lowerLimit, c.nominalValue, c.upperLimit]
        .map(v => v ?? '—').join(' / ');
    }
    if (c.characteristicType === 'COMMON') {
      return c.expectedBoolean ? 'Conforms = true' : 'Conforms = false';
    }
    return c.expression ?? '—';
  }

  openAdd(): void {
    this.editing = false;
    this.editId = null;
    this.serverError = '';
    this.draft = { source: 'DESIGN', characteristicType: 'SPECIFIC', sampleSizeRule: 'ALL' };
    this.showDialog = true;
  }

  openEdit(c: CharacteristicDto): void {
    this.editing = true;
    this.editId = c.id;
    this.serverError = '';
    this.draft = { ...c };
    this.showDialog = true;
  }

  onTypeChange(): void {
    // Clear fields not applicable to the selected type so stale values aren't submitted.
    this.draft.nominalValue = undefined;
    this.draft.lowerLimit = undefined;
    this.draft.upperLimit = undefined;
    this.draft.expectedBoolean = undefined;
    this.draft.expression = undefined;
  }

  canSave(): boolean {
    if (!this.draft.characteristicNumber || !this.draft.name) return false;
    const type: CharacteristicType | undefined = this.draft.characteristicType;
    if (type === 'CALCULATED' && !this.draft.expression) return false;
    if (type === 'COMMON' && this.draft.expectedBoolean === undefined) return false;
    if (type === 'SPECIFIC'
        && this.draft.nominalValue == null
        && this.draft.lowerLimit == null
        && this.draft.upperLimit == null) {
      return false;
    }
    const rule: SampleSizeRule | undefined = this.draft.sampleSizeRule;
    if (rule === 'FIXED_COUNT' && !this.draft.sampleSizeCount) return false;
    return true;
  }

  save(): void {
    this.saving = true;
    this.serverError = '';
    const req = this.draft;
    const obs = this.editing && this.editId
      ? this.api.updateCharacteristic(this.planId, this.editId, req)
      : this.api.addCharacteristic(this.planId, req);
    obs.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.saving = false;
        this.showDialog = false;
        this.messageService.add({ severity: 'success', summary: 'Characteristic saved' });
        this.load();
        this.cdr.detectChanges();
      },
      error: err => {
        this.saving = false;
        this.serverError = this.extractError(err);
        this.cdr.detectChanges();
      },
    });
  }

  remove(c: CharacteristicDto): void {
    this.api.deleteCharacteristic(this.planId, c.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.messageService.add({ severity: 'success', summary: 'Characteristic deleted' });
          this.load();
          this.cdr.detectChanges();
        },
        error: err => {
          this.messageService.add({ severity: 'error', summary: this.extractError(err) });
          this.cdr.detectChanges();
        },
      });
  }

  private extractError(err: unknown): string {
    const e = err as { error?: { error?: string; details?: string[] } };
    if (e?.error?.details?.length) return e.error.details.join('; ');
    return e?.error?.error ?? 'Operation failed';
  }
}
