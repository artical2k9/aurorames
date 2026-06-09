import {
  ChangeDetectorRef, Component, EventEmitter, inject, Input, OnChanges,
  OnInit, Output, SimpleChanges,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { InputNumberModule } from 'primeng/inputnumber';
import { TextareaModule } from 'primeng/textarea';
import { PanelModule } from 'primeng/panel';
import { MessageModule } from 'primeng/message';
import { CheckboxModule } from 'primeng/checkbox';
import { UdfFieldsComponent } from '../../../../shared/udf/udf-fields.component';
import { ItemMasterApiService } from '../../services/item-master-api.service';
import { ItemMasterDto, Classification, MakeBuyCode, TraceabilityMethod, CounterfeitRiskLevel } from '../../models/item-master.model';

@Component({
  selector: 'app-item-master-form',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    DialogModule, ButtonModule, InputTextModule, SelectModule,
    ToggleSwitchModule, InputNumberModule, TextareaModule,
    PanelModule, MessageModule, CheckboxModule, UdfFieldsComponent,
  ],
  template: `
    <p-dialog
      [header]="editMode ? 'Edit Item' : 'New Item'"
      [(visible)]="visible"
      (onHide)="onHide()"
      [modal]="true"
      [style]="{ width: '720px' }"
      [draggable]="false"
    >
      @if (serverErrors.length) {
        <div class="form-errors">
          @for (err of serverErrors; track err) {
            <p-message severity="error" [text]="err" />
          }
        </div>
      }

      <form [formGroup]="form" class="imf" (ngSubmit)="save()">

        <!-- ── Identity ───────────────────────────────────────── -->
        <fieldset class="imf__section">
          <legend class="imf__legend">Identity</legend>
          <div class="imf__grid">
            <div class="imf__field">
              <label>Part Number <span class="imf__req">*</span></label>
              @if (editMode) {
                <span class="imf__readonly">{{ form.get('partNumber')?.value }}</span>
              } @else {
                <input pInputText formControlName="partNumber" placeholder="e.g. PN-001" />
              }
            </div>
            <div class="imf__field imf__field--full">
              <label>Description <span class="imf__req">*</span></label>
              <input pInputText formControlName="description" placeholder="Brief item description" />
            </div>
          </div>
        </fieldset>

        <!-- ── Classification ─────────────────────────────────── -->
        <fieldset class="imf__section">
          <legend class="imf__legend">Classification</legend>
          <div class="imf__grid">
            <div class="imf__field">
              <label>Classification <span class="imf__req">*</span></label>
              <p-select formControlName="classification" [options]="classificationOptions"
                        placeholder="Select…" optionLabel="label" optionValue="value" />
            </div>
            <div class="imf__field">
              <label>Make/Buy <span class="imf__req">*</span></label>
              <p-select formControlName="makeBuyCode" [options]="makeBuyOptions"
                        placeholder="Select…" optionLabel="label" optionValue="value" />
            </div>
            <div class="imf__field">
              <label>Traceability <span class="imf__req">*</span></label>
              @if (editMode) {
                <span class="imf__readonly">{{ form.get('traceabilityMethod')?.value }}</span>
              } @else {
                <p-select formControlName="traceabilityMethod" [options]="traceabilityOptions"
                          placeholder="Select…" optionLabel="label" optionValue="value" />
              }
            </div>
            <div class="imf__field">
              <label>Unit of Measure <span class="imf__req">*</span></label>
              <input pInputText formControlName="unitOfMeasure" placeholder="e.g. EA" />
            </div>
            <div class="imf__field">
              <label>CAGE Code</label>
              <input pInputText formControlName="cageCode" placeholder="Optional" />
            </div>
          </div>
        </fieldset>

        <!-- ── Shelf Life ─────────────────────────────────────── -->
        <fieldset class="imf__section">
          <legend class="imf__legend">Shelf Life</legend>
          <div class="imf__toggle-row">
            <p-toggleswitch formControlName="shelfLifeControlled" />
            <label>Shelf life controlled</label>
          </div>
          @if (form.get('shelfLifeControlled')?.value) {
            <div class="imf__field" style="margin-top: 0.75rem;">
              <label>Shelf Life Days <span class="imf__req">*</span></label>
              <p-inputnumber formControlName="shelfLifeDays" [min]="1" [showButtons]="false"
                             placeholder="Days" styleClass="imf__number" />
            </div>
          }
        </fieldset>

        <!-- ── AS5553 ──────────────────────────────────────────── -->
        <p-panel header="AS5553 Counterfeit Risk" [toggleable]="true" [collapsed]="true"
                 styleClass="imf__panel">
          <div class="imf__grid">
            <div class="imf__field">
              <label>Risk Level</label>
              <p-select formControlName="counterfeitRiskLevel" [options]="riskOptions"
                        placeholder="Select…" optionLabel="label" optionValue="value"
                        [showClear]="true" />
            </div>
            <div class="imf__field imf__field--full">
              <label>Approved Suppliers</label>
              <textarea pTextarea formControlName="approvedSuppliers" rows="3"
                        placeholder="One supplier per line"></textarea>
            </div>
            <div class="imf__toggle-row">
              <p-toggleswitch formControlName="verificationRequired" />
              <label>Verification required</label>
            </div>
          </div>
        </p-panel>

        <!-- ── UDF Fields ─────────────────────────────────────── -->
        <app-udf-fields
          moduleKey="ITEM_MASTER"
          [udfGroup]="udfGroup"
          [initialValues]="loadedItem?.customFields ?? {}"
        />

      </form>

      <ng-template pTemplate="footer">
        <p-button label="Cancel" severity="secondary" (onClick)="onHide()" />
        <p-button [label]="editMode ? 'Save Changes' : 'Create Item'"
                  severity="primary"
                  [loading]="saving"
                  [disabled]="form.invalid"
                  (onClick)="save()" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .form-errors { margin-bottom: 1rem; display: flex; flex-direction: column; gap: 0.25rem; }
    .imf { display: flex; flex-direction: column; gap: 1rem; }
    .imf__section { border: 1px solid var(--p-surface-border); border-radius: 6px; padding: 1rem; margin: 0; }
    .imf__legend { font-size: 0.8125rem; font-weight: 600; color: var(--p-text-muted-color);
                   padding: 0 0.375rem; text-transform: uppercase; letter-spacing: 0.05em; }
    .imf__grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0.875rem; }
    .imf__field { display: flex; flex-direction: column; gap: 0.3rem; }
    .imf__field--full { grid-column: 1 / -1; }
    .imf__field label { font-size: 0.8125rem; font-weight: 500; color: var(--p-text-muted-color); }
    .imf__req { color: #EF4444; }
    .imf__readonly { font-size: 0.9375rem; font-weight: 500; padding: 0.375rem 0; }
    .imf__toggle-row { display: flex; align-items: center; gap: 0.5rem; }
    .imf__panel { margin-top: 0; }
    :host ::ng-deep .imf__number input { width: 120px; }
    :host ::ng-deep .imf__panel .p-panel { border: 1px solid var(--p-surface-border); border-radius: 6px; }
  `],
})
export class ItemMasterFormComponent implements OnInit, OnChanges {
  @Input() visible = false;
  @Input() itemId?: string;
  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() saved = new EventEmitter<ItemMasterDto>();

  private readonly fb  = inject(FormBuilder);
  private readonly api = inject(ItemMasterApiService);
  private readonly cdr = inject(ChangeDetectorRef);

  form = this.fb.group({
    partNumber: ['', Validators.required],
    description: ['', Validators.required],
    classification: [null as string | null, Validators.required],
    makeBuyCode: [null as string | null, Validators.required],
    traceabilityMethod: [null as string | null, Validators.required],
    unitOfMeasure: ['', Validators.required],
    cageCode: [''],
    shelfLifeControlled: [false],
    shelfLifeDays: [null as number | null],
    counterfeitRiskLevel: [null as string | null],
    verificationRequired: [false],
    approvedSuppliers: [''],
    udfValues: this.fb.group({}),
  });

  loadedItem: ItemMasterDto | null = null;
  serverErrors: string[] = [];
  saving = false;

  get udfGroup(): FormGroup {
    return this.form.get('udfValues') as FormGroup;
  }

  get editMode(): boolean { return !!this.itemId; }

  readonly classificationOptions = [
    { label: 'Assembly',       value: 'ASSEMBLY' },
    { label: 'COTS',           value: 'COTS' },
    { label: 'Fabricated',     value: 'FABRICATED' },
    { label: 'Purchased Part', value: 'PURCHASED_PART' },
    { label: 'Raw Material',   value: 'RAW_MATERIAL' },
    { label: 'Service',        value: 'SERVICE' },
  ];

  readonly makeBuyOptions = [
    { label: 'Make',   value: 'MAKE' },
    { label: 'Buy',    value: 'BUY' },
    { label: 'Either', value: 'EITHER' },
  ];

  readonly traceabilityOptions = [
    { label: 'Serial',    value: 'SERIAL' },
    { label: 'Lot',       value: 'LOT' },
    { label: 'Heat Code', value: 'HEAT_CODE' },
    { label: 'Date Code', value: 'DATE_CODE' },
    { label: 'None',      value: 'NONE' },
  ];

  readonly riskOptions = [
    { label: 'Low',      value: 'LOW' },
    { label: 'Medium',   value: 'MEDIUM' },
    { label: 'High',     value: 'HIGH' },
    { label: 'Critical', value: 'CRITICAL' },
  ];

  ngOnInit(): void {
    this.form.get('shelfLifeControlled')!.valueChanges.subscribe(on => {
      const ctrl = this.form.get('shelfLifeDays')!;
      if (on) {
        ctrl.addValidators(Validators.required);
      } else {
        ctrl.clearValidators();
        ctrl.setValue(null);
      }
      ctrl.updateValueAndValidity();
    });
  }

  private buildCustomFields(): Record<string, unknown> | undefined {
    const keys = Object.keys(this.udfGroup.controls);
    if (keys.length === 0) return undefined;
    const result: Record<string, unknown> = {};
    keys.forEach(key => {
      const val = this.udfGroup.get(key)?.value;
      if (val !== null && val !== undefined && val !== '') result[key] = val;
    });
    return Object.keys(result).length ? result : undefined;
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['visible']?.currentValue && this.itemId) {
      this.loadedItem = null;
      this.loadItem(this.itemId);
    }
    if (changes['visible']?.currentValue && !this.itemId) {
      this.loadedItem = null;
      this.form.reset({ shelfLifeControlled: false, verificationRequired: false });
      this.serverErrors = [];
    }
  }

  private loadItem(id: string): void {
    this.api.getById(id).subscribe(item => {
      this.loadedItem = item;
      this.form.patchValue({
        partNumber:          item.partNumber,
        description:         item.description,
        classification:      item.classification,
        makeBuyCode:         item.makeBuyCode,
        traceabilityMethod:  item.traceabilityMethod,
        unitOfMeasure:       item.unitOfMeasure,
        cageCode:            item.cageCode ?? '',
        shelfLifeControlled: item.shelfLifeControlled,
        shelfLifeDays:       item.shelfLifeDays ?? null,
        counterfeitRiskLevel: item.counterfeitRiskLevel ?? null,
        verificationRequired: item.verificationRequired,
        approvedSuppliers:   (item.approvedSuppliers ?? []).join('\n'),
      });
      this.cdr.detectChanges();
    });
  }

  save(): void {
    if (this.form.invalid) return;
    this.saving = true;
    this.serverErrors = [];
    const v = this.form.value;

    const obs = this.editMode
      ? this.api.patch(this.itemId!, {
          description:          v.description ?? undefined,
          unitOfMeasure:        v.unitOfMeasure ?? undefined,
          cageCode:             v.cageCode ?? undefined,
          classification:       (v.classification as Classification) ?? undefined,
          makeBuyCode:          (v.makeBuyCode as MakeBuyCode) ?? undefined,
          shelfLifeControlled:  v.shelfLifeControlled ?? undefined,
          shelfLifeDays:        v.shelfLifeDays ?? null,
          counterfeitRiskLevel: (v.counterfeitRiskLevel as CounterfeitRiskLevel) ?? null,
          verificationRequired: v.verificationRequired ?? undefined,
          approvedSuppliers:    v.approvedSuppliers
            ? v.approvedSuppliers.split('\n').map((s: string) => s.trim()).filter(Boolean)
            : [],
          customFields: this.buildCustomFields(),
        })
      : this.api.create({
          partNumber: v.partNumber!,
          description: v.description!,
          unitOfMeasure: v.unitOfMeasure!,
          classification: v.classification as Classification,
          makeBuyCode: v.makeBuyCode as MakeBuyCode,
          traceabilityMethod: v.traceabilityMethod as TraceabilityMethod,
        });

    obs.subscribe({
      next: item => {
        this.saving = false;
        this.saved.emit(item);
        this.onHide();
      },
      error: (err: { status: number; error?: { violations?: { field: string; message: string }[] } }) => {
        this.saving = false;
        if (err.status === 422 && err.error?.violations) {
          this.serverErrors = err.error.violations.map(v => `${v.field}: ${v.message}`);
        }
        this.cdr.detectChanges();
      },
    });
  }

  onHide(): void {
    this.visibleChange.emit(false);
  }
}
