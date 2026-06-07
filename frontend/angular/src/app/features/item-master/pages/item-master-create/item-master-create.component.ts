import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { InputNumberModule } from 'primeng/inputnumber';
import { TextareaModule } from 'primeng/textarea';
import { MessageModule } from 'primeng/message';
import { SkeletonModule } from 'primeng/skeleton';
import { BreadcrumbService } from '../../../../shared/ui';
import { UdfFieldsComponent } from '../../../../shared/udf/udf-fields.component';
import { ItemMasterApiService } from '../../services/item-master-api.service';
import {
  Classification, TraceabilityMethod, CounterfeitRiskLevel, MakeBuyCode,
} from '../../models/item-master.model';

@Component({
  selector: 'app-item-master-create',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    ButtonModule, InputTextModule, SelectModule,
    ToggleSwitchModule, InputNumberModule, TextareaModule, MessageModule, SkeletonModule,
    UdfFieldsComponent,
  ],
  template: `
    <div class="imcr">

      <div class="imcr__header">
        <div>
          <h2 class="imcr__title">New Item</h2>
          <p class="imcr__subtitle">* Required fields</p>
        </div>
        <div class="imcr__header-actions">
          <p-button label="Cancel" severity="secondary" size="small" (onClick)="cancel()" />
          <p-button label="Save Item" severity="primary" size="small"
                    [loading]="saving" [disabled]="!canSave()" (onClick)="save()" />
        </div>
      </div>

      @if (cloneLoading) {
        <div class="imcr__skeleton-row">
          @for (i of [1,2,3,4]; track i) { <p-skeleton height="2rem" /> }
        </div>
      }

      @if (serverErrors.length) {
        <div class="imcr__errors">
          @for (err of serverErrors; track err) {
            <p-message severity="error" [text]="err" />
          }
        </div>
      }

      <form [formGroup]="form" class="imcr__form">
        <div class="imcr__two-col">

          <!-- Left: Core Identification -->
          <div class="imcr__section">
            <h3 class="imcr__section-title">Core Identification</h3>

            <div class="imcr__field">
              <label class="imcr__label">Part Number <span class="imcr__req">*</span></label>
              <input pInputText formControlName="partNumber" placeholder="e.g. PN-001" />
            </div>

            <div class="imcr__field">
              <label class="imcr__label">Revision <span class="imcr__req">*</span></label>
              <input pInputText formControlName="revision" placeholder="e.g. A" />
            </div>

            <div class="imcr__field">
              <label class="imcr__label">Description <span class="imcr__req">*</span></label>
              <textarea pTextarea formControlName="description" rows="3"
                        placeholder="Brief item description" style="width:100%"></textarea>
            </div>

            <div class="imcr__field">
              <label class="imcr__label">Unit of Measure <span class="imcr__req">*</span></label>
              <p-select formControlName="unitOfMeasure" [options]="uomOptions"
                        optionLabel="label" optionValue="value"
                        [editable]="true" placeholder="Each (EA)" />
            </div>

            <div class="imcr__field">
              <label class="imcr__label">Classification <span class="imcr__req">*</span></label>
              <p-select formControlName="classification" [options]="classificationOptions"
                        optionLabel="label" optionValue="value" placeholder="Select…" />
            </div>

            <div class="imcr__field">
              <label class="imcr__label">Make / Buy Code <span class="imcr__req">*</span></label>
              <div class="imcr__makebuy">
                <p-button label="Make" size="small" type="button"
                          [severity]="makeActive ? 'primary' : 'secondary'"
                          (onClick)="toggleMake()" />
                <p-button label="Buy" size="small" type="button"
                          [severity]="buyActive ? 'primary' : 'secondary'"
                          (onClick)="toggleBuy()" />
              </div>
              @if (makeBuyTouched && !makeActive && !buyActive) {
                <small class="imcr__error-text">At least one of Make or Buy must be selected</small>
              }
            </div>
          </div>

          <!-- Right: Traceability & Compliance -->
          <div class="imcr__section">
            <h3 class="imcr__section-title">Traceability & Compliance</h3>

            <!-- Traceability method multi-select (FR-033b) -->
            <div class="imcr__field">
              <label class="imcr__label">Traceability Method <span class="imcr__req">*</span></label>
              <div class="imcr__trace-btns">
                @for (opt of traceOptions; track opt.value) {
                  <p-button [label]="opt.label" size="small" type="button"
                            [severity]="traceHas(opt.value) ? 'primary' : 'secondary'"
                            [disabled]="!traceEnabled(opt.value)"
                            [title]="opt.hint ?? opt.label"
                            (onClick)="toggleTrace(opt.value)" />
                }
              </div>
              @if (traceabilityTouched && selectedTraceability.size === 0) {
                <small class="imcr__error-text">Select at least one traceability method</small>
              }
            </div>

            <!-- Shelf Life Controlled (T-3) -->
            <div class="imcr__field">
              <div class="imcr__toggle-row">
                <p-toggleswitch formControlName="shelfLifeControlled" />
                <label class="imcr__label">Shelf Life Controlled</label>
              </div>
              @if (!shelfLifeAllowed) {
                <small class="imcr__hint-text">Requires Serial or Lot traceability</small>
              }
              <!-- T-4: days shown when shelf-life=true AND make active -->
              @if (form.get('shelfLifeControlled')?.value && shelfLifeDaysActive) {
                <div class="imcr__field" style="margin-top:0.5rem">
                  <label class="imcr__label">Shelf Life Days <span class="imcr__req">*</span></label>
                  <p-inputnumber formControlName="shelfLifeDays" [min]="1" placeholder="Days" />
                </div>
              }
              <!-- T-5: buy-only hint -->
              @if (form.get('shelfLifeControlled')?.value && !shelfLifeDaysActive && buyActive && !makeActive) {
                <small class="imcr__hint-text">Shelf Life Days not applicable for Buy-only items</small>
              }
            </div>

            <div class="imcr__field">
              <label class="imcr__label">CAGE Code</label>
              <input pInputText formControlName="cageCode" placeholder="Optional" />
            </div>

            <div class="imcr__field">
              <label class="imcr__label">Counterfeit Risk Level</label>
              <p-select formControlName="counterfeitRiskLevel" [options]="riskOptions"
                        optionLabel="label" optionValue="value"
                        placeholder="Select…" [showClear]="true" />
            </div>

            <div class="imcr__field">
              <div class="imcr__toggle-row">
                <p-toggleswitch formControlName="verificationRequired" />
                <label class="imcr__label">Verification Required</label>
              </div>
            </div>

            <div class="imcr__field">
              <label class="imcr__label">STEP Part Reference</label>
              <input pInputText formControlName="stepPartRef" placeholder="e.g. S000-BRKT-001" />
            </div>
          </div>
        </div>

        <app-udf-fields moduleKey="ITEM_MASTER" [udfGroup]="udfGroup" />
      </form>

    </div>
  `,
  styles: [`
    .imcr { padding: 1.25rem; }

    .imcr__header {
      display: flex; align-items: flex-start; justify-content: space-between;
      margin-bottom: 1.25rem;
    }
    .imcr__header-actions { display: flex; gap: 0.5rem; }
    .imcr__title { margin: 0; font-size: 1.375rem; font-weight: 700; }
    .imcr__subtitle { margin: 0.25rem 0 0; font-size: 0.8125rem; color: var(--p-text-muted-color); }

    .imcr__skeleton-row {
      display: grid; grid-template-columns: repeat(4, 1fr); gap: 0.75rem; margin-bottom: 1rem;
    }

    .imcr__errors { display: flex; flex-direction: column; gap: 0.25rem; margin-bottom: 1rem; }

    .imcr__form { display: flex; flex-direction: column; gap: 1.25rem; }

    .imcr__two-col { display: grid; grid-template-columns: 1fr 1fr; gap: 1.25rem; }

    .imcr__section {
      border: 1px solid var(--p-surface-border); border-radius: 8px;
      padding: 1.25rem; display: flex; flex-direction: column; gap: 0.875rem;
    }
    .imcr__section-title {
      margin: 0 0 0.25rem; font-size: 0.9375rem; font-weight: 600;
    }

    .imcr__field { display: flex; flex-direction: column; gap: 0.3rem; }
    .imcr__label { font-size: 0.8125rem; font-weight: 500; color: var(--p-text-muted-color); }
    .imcr__req { color: #EF4444; }
    .imcr__error-text { font-size: 0.75rem; color: #EF4444; margin-top: 0.25rem; }
    .imcr__hint-text { font-size: 0.75rem; color: var(--p-text-muted-color); margin-top: 0.2rem; }

    .imcr__toggle-row { display: flex; align-items: center; gap: 0.5rem; }
    .imcr__makebuy { display: flex; gap: 0.5rem; }
    .imcr__trace-btns { display: flex; flex-wrap: wrap; gap: 0.5rem; }

  `],
})
export class ItemMasterCreateComponent implements OnInit {
  private readonly fb            = inject(FormBuilder);
  private readonly api           = inject(ItemMasterApiService);
  private readonly router        = inject(Router);
  private readonly route         = inject(ActivatedRoute);
  private readonly breadcrumbSvc = inject(BreadcrumbService);
  private readonly cdr           = inject(ChangeDetectorRef);

  readonly uomOptions = [
    { label: 'Each (EA)',      value: 'EA' },
    { label: 'Kilogram (KG)', value: 'KG' },
    { label: 'Metre (M)',     value: 'M' },
    { label: 'Litre (L)',     value: 'L' },
    { label: 'Piece (PC)',    value: 'PC' },
    { label: 'Set (SET)',     value: 'SET' },
  ];

  readonly classificationOptions = [
    { label: 'Assembly',       value: 'ASSEMBLY' },
    { label: 'COTS',           value: 'COTS' },
    { label: 'Fabricated',     value: 'FABRICATED' },
    { label: 'Purchased Part', value: 'PURCHASED_PART' },
    { label: 'Raw Material',   value: 'RAW_MATERIAL' },
    { label: 'Service',        value: 'SERVICE' },
  ];

  readonly traceOptions: { label: string; value: TraceabilityMethod; hint?: string }[] = [
    { label: 'None',      value: 'NONE' },
    { label: 'Serial',    value: 'SERIAL' },
    { label: 'Lot',       value: 'LOT' },
    { label: 'Heat Code', value: 'HEAT_CODE', hint: 'Requires Lot control' },
    { label: 'Date Code', value: 'DATE_CODE', hint: 'Requires Lot control' },
  ];

  readonly riskOptions = [
    { label: 'Low',      value: 'LOW' },
    { label: 'Medium',   value: 'MEDIUM' },
    { label: 'High',     value: 'HIGH' },
    { label: 'Critical', value: 'CRITICAL' },
  ];

  form = this.fb.group({
    partNumber:           ['', Validators.required],
    revision:             ['', Validators.required],
    description:          ['', Validators.required],
    unitOfMeasure:        ['EA', Validators.required],
    classification:       [null as Classification | null, Validators.required],
    cageCode:             [''],
    shelfLifeControlled:  [false],
    shelfLifeDays:        [null as number | null],
    counterfeitRiskLevel: [null as CounterfeitRiskLevel | null],
    verificationRequired: [false],
    stepPartRef:          [''],
    udfValues:            this.fb.group({}),
  });

  serverErrors: string[] = [];
  saving = false;
  cloneLoading = false;

  makeActive = false;
  buyActive = false;
  makeBuyTouched = false;

  selectedTraceability = new Set<TraceabilityMethod>();
  traceabilityTouched = false;

  // ── Make/Buy ─────────────────────────────────────────────────────────────────

  get derivedMakeBuyCode(): MakeBuyCode | null {
    if (this.makeActive && this.buyActive) return 'EITHER';
    if (this.makeActive) return 'MAKE';
    if (this.buyActive) return 'BUY';
    return null;
  }

  canSave(): boolean {
    return this.form.valid
      && this.derivedMakeBuyCode !== null
      && this.selectedTraceability.size > 0;
  }

  toggleMake(): void {
    this.makeActive = !this.makeActive;
    this.makeBuyTouched = true;
    this.updateShelfLifeDaysValidity();
  }

  toggleBuy(): void {
    this.buyActive = !this.buyActive;
    this.makeBuyTouched = true;
    this.updateShelfLifeDaysValidity();
  }

  // ── Traceability helpers (FR-033b) ───────────────────────────────────────────

  traceHas(m: TraceabilityMethod): boolean {
    return this.selectedTraceability.has(m);
  }

  traceEnabled(m: TraceabilityMethod): boolean {
    if (m === 'HEAT_CODE' || m === 'DATE_CODE') return this.selectedTraceability.has('LOT');
    return true;
  }

  toggleTrace(m: TraceabilityMethod): void {
    this.traceabilityTouched = true;
    if (m === 'NONE') {
      this.selectedTraceability.clear();
      this.selectedTraceability.add('NONE');
    } else {
      this.selectedTraceability.delete('NONE');
      if (this.selectedTraceability.has(m)) {
        this.selectedTraceability.delete(m);
        if (m === 'LOT') {
          this.selectedTraceability.delete('HEAT_CODE'); // T-1
          this.selectedTraceability.delete('DATE_CODE'); // T-1
        }
      } else {
        this.selectedTraceability.add(m);
      }
    }
    if (!this.shelfLifeAllowed) {
      this.form.get('shelfLifeControlled')?.setValue(false); // T-6
    }
    this.updateShelfLifeDaysValidity();
  }

  get shelfLifeAllowed(): boolean {
    // T-3: enabled only with Serial/Lot/HeatCode/DateCode
    const s = this.selectedTraceability;
    if (s.size === 0 || s.has('NONE')) return false;
    return s.has('SERIAL') || s.has('LOT') || s.has('HEAT_CODE') || s.has('DATE_CODE');
  }

  get shelfLifeDaysActive(): boolean {
    // T-4/T-5: days shown only when shelf-life=true AND make is active (not buy-only)
    return !!this.form.get('shelfLifeControlled')?.value && this.makeActive;
  }

  get derivedTraceabilityMethod(): TraceabilityMethod {
    const s = this.selectedTraceability;
    if (s.has('HEAT_CODE')) return 'HEAT_CODE';
    if (s.has('DATE_CODE')) return 'DATE_CODE';
    if (s.has('LOT'))       return 'LOT';
    if (s.has('SERIAL'))    return 'SERIAL';
    return 'NONE';
  }

  private updateShelfLifeDaysValidity(): void {
    const ctrl = this.form.get('shelfLifeDays')!;
    if (this.shelfLifeDaysActive) {
      ctrl.addValidators(Validators.required);
    } else {
      ctrl.clearValidators();
      ctrl.setValue(null);
    }
    ctrl.updateValueAndValidity();
  }

  private initTraceability(method: TraceabilityMethod): void {
    this.selectedTraceability.clear();
    if (method === 'HEAT_CODE') {
      this.selectedTraceability.add('LOT');
      this.selectedTraceability.add('HEAT_CODE');
    } else if (method === 'DATE_CODE') {
      this.selectedTraceability.add('LOT');
      this.selectedTraceability.add('DATE_CODE');
    } else {
      this.selectedTraceability.add(method);
    }
  }

  get udfGroup(): FormGroup {
    return this.form.get('udfValues') as FormGroup;
  }

  // ── Lifecycle ─────────────────────────────────────────────────────────────────

  ngOnInit(): void {
    this.breadcrumbSvc.set([
      { label: 'Master Data' },
      { label: 'Item Master', route: ['/item-master'] },
      { label: 'New Item' },
    ]);

    this.form.get('shelfLifeControlled')!.valueChanges.subscribe(() => {
      this.updateShelfLifeDaysValidity();
    });

    const cloneFrom = this.route.snapshot.queryParamMap.get('cloneFrom');
    if (cloneFrom) {
      this.cloneLoading = true;
      this.api.getById(cloneFrom).subscribe({
        next: item => {
          this.cloneLoading = false;
          this.form.patchValue({
            description:          item.description,
            unitOfMeasure:        item.unitOfMeasure,
            classification:       item.classification,
            cageCode:             item.cageCode ?? '',
            shelfLifeControlled:  item.shelfLifeControlled,
            shelfLifeDays:        item.shelfLifeDays ?? null,
            counterfeitRiskLevel: item.counterfeitRiskLevel ?? null,
            verificationRequired: item.verificationRequired,
            stepPartRef:          item.stepPartRef ?? '',
          });
          this.makeActive = item.makeBuyCode === 'MAKE' || item.makeBuyCode === 'EITHER';
          this.buyActive  = item.makeBuyCode === 'BUY'  || item.makeBuyCode === 'EITHER';
          this.initTraceability(item.traceabilityMethod);
          this.updateShelfLifeDaysValidity();
          this.cdr.detectChanges();
        },
        error: () => { this.cloneLoading = false; this.cdr.detectChanges(); },
      });
    }
  }

  cancel(): void {
    this.router.navigate(['/item-master']);
  }

  save(): void {
    this.makeBuyTouched = true;
    this.traceabilityTouched = true;
    if (this.form.invalid || !this.derivedMakeBuyCode || this.selectedTraceability.size === 0) return;

    this.saving = true;
    this.serverErrors = [];
    const v = this.form.value;

    this.api.create({
      partNumber:           v.partNumber!,
      revision:             v.revision!,
      description:          v.description!,
      unitOfMeasure:        v.unitOfMeasure!,
      classification:       v.classification as Classification,
      makeBuyCode:          this.derivedMakeBuyCode,
      traceabilityMethod:   this.derivedTraceabilityMethod,
      cageCode:             v.cageCode || undefined,
      shelfLifeControlled:  v.shelfLifeControlled ?? undefined,
      shelfLifeDays:        this.shelfLifeDaysActive ? (v.shelfLifeDays ?? undefined) : undefined,
      counterfeitRiskLevel: (v.counterfeitRiskLevel as CounterfeitRiskLevel) ?? undefined,
      verificationRequired: v.verificationRequired ?? undefined,
      stepPartRef:          v.stepPartRef || undefined,
      customFields:         this.buildCustomFields(),
    }).subscribe({
      next: item => {
        this.saving = false;
        this.router.navigate(['/item-master', item.id]);
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
}
