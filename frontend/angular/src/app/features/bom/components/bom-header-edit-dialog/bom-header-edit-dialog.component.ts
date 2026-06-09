import { Component, EventEmitter, inject, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { MessageModule } from 'primeng/message';
import { UdfFieldsComponent } from '../../../../shared/udf/udf-fields.component';
import { BomApiService } from '../../services/bom-api.service';
import { BomDto, PatchBomHeaderRequest } from '../../models/bom.model';

@Component({
  selector: 'app-bom-header-edit-dialog',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    DialogModule, ButtonModule, InputTextModule, SelectModule, MessageModule,
    UdfFieldsComponent,
  ],
  template: `
    <p-dialog header="Edit BOM Header" [(visible)]="visible" [modal]="true"
              [style]="{ width: '500px' }"
              (visibleChange)="visibleChange.emit($event)">

      @if (serverError) {
        <p-message severity="error" [text]="serverError" />
      }

      @if (bom) {
        <form [formGroup]="form" class="bhd__form">

          <div class="bhd__field">
            <label>Part Number</label>
            <span class="bhd__readonly">🔒 {{ bom.parentItemId }}</span>
          </div>

          <div class="bhd__field">
            <label>BOM Description</label>
            <input pInputText formControlName="description" />
          </div>

          <div class="bhd__field">
            <label>Reason for Revision</label>
            <input pInputText formControlName="reasonForRevision" />
          </div>

          <div class="bhd__field">
            <label>Production Line</label>
            <input pInputText formControlName="productionLine" placeholder="e.g. LINE-A" />
          </div>

          <div class="bhd__section-header">BOM HEADER PROPERTIES</div>

          <div class="bhd__field">
            <label>BOM Type</label>
            <p-select formControlName="bomType" [options]="bomTypeOptions"
                      optionLabel="label" optionValue="value"
                      placeholder="Select…" [showClear]="true" />
          </div>

          <div class="bhd__field">
            <label>Effectivity Type</label>
            <p-select formControlName="effectivityType" [options]="effectivityTypeOptions"
                      optionLabel="label" optionValue="value"
                      placeholder="Select…" [showClear]="true" />
          </div>

          <app-udf-fields
            moduleKey="BOM_HEADER"
            [udfGroup]="udfGroup"
            [initialValues]="bom.customFields ?? {}"
          />

        </form>
      }

      <ng-template pTemplate="footer">
        <p-button label="Cancel" severity="secondary" size="small"
                  (onClick)="close()" />
        <p-button label="Save Changes" severity="primary" size="small"
                  [loading]="saving" (onClick)="save()" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .bhd__form { display: flex; flex-direction: column; gap: 0.75rem; }
    .bhd__field { display: flex; flex-direction: column; gap: 0.25rem; }
    .bhd__readonly { font-size: 0.8125rem; color: var(--p-text-muted-color); }
    .bhd__section-header {
      font-size: 0.6875rem; font-weight: 700; letter-spacing: 0.08em;
      text-transform: uppercase; color: var(--p-primary-color);
      margin: 0.5rem 0 0.25rem;
    }
    .bhd__udf-title { margin: 0.5rem 0 0.25rem; font-size: 0.875rem; font-weight: 600; }
  `],
})
export class BomHeaderEditDialogComponent implements OnChanges {
  @Input() visible = false;
  @Input() bom: BomDto | null = null;
  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() saved = new EventEmitter<BomDto>();

  private readonly bomApi = inject(BomApiService);
  private readonly fb = inject(FormBuilder);

  saving = false;
  serverError = '';

  get udfGroup(): FormGroup {
    return this.form.get('udfValues') as FormGroup;
  }

  readonly bomTypeOptions = [
    { label: 'Manufacturing BOM', value: 'MANUFACTURING' },
    { label: 'Engineering BOM',   value: 'ENGINEERING' },
    { label: 'Service BOM',       value: 'SERVICE' },
  ];

  readonly effectivityTypeOptions = [
    { label: 'Date', value: 'DATE' },
    { label: 'Unit', value: 'UNIT' },
    { label: 'None', value: 'NONE' },
  ];

  form = this.fb.group({
    description:        [''],
    reasonForRevision:  [''],
    productionLine:     [''],
    bomType:            [''],
    effectivityType:    [''],
    udfValues:          this.fb.group({}),
  });

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['visible'] && this.visible && this.bom) {
      this.form.patchValue({
        description:       this.bom.description ?? '',
        reasonForRevision: this.bom.reasonForRevision ?? '',
        productionLine:    this.bom.productionLine ?? '',
        bomType:           this.bom.bomType ?? '',
        effectivityType:   this.bom.effectivityType ?? '',
      });
      this.serverError = '';
    }
  }

  close(): void {
    this.visibleChange.emit(false);
  }

  save(): void {
    this.saving = true;
    this.serverError = '';
    const v = this.form.value;
    const customFields: Record<string, unknown> = {};
    Object.keys(this.udfGroup.controls).forEach(key => {
      const val = this.udfGroup.get(key)?.value;
      if (val !== null && val !== undefined && val !== '') customFields[key] = val;
    });
    const req: PatchBomHeaderRequest = {
      description:       v.description || undefined,
      reasonForRevision: v.reasonForRevision || undefined,
      productionLine:    v.productionLine || undefined,
      bomType:           v.bomType || undefined,
      effectivityType:   v.effectivityType || undefined,
      customFields:      Object.keys(customFields).length ? customFields : undefined,
    };
    this.bomApi.patchHeader(this.bom!.id, req).subscribe({
      next: bom => {
        this.saving = false;
        this.saved.emit(bom);
      },
      error: (err: { status: number; error?: { message?: string } }) => {
        this.saving = false;
        this.serverError = err.error?.message ?? 'Failed to save';
      },
    });
  }
}
