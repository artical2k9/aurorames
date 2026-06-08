import { ChangeDetectorRef, Component, DestroyRef, EventEmitter, inject, Input, OnInit, Output } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { SelectModule } from 'primeng/select';
import { DatePickerModule } from 'primeng/datepicker';
import { MessageModule } from 'primeng/message';
import { AutoCompleteModule, AutoCompleteSelectEvent } from 'primeng/autocomplete';
import { BomApiService } from '../../services/bom-api.service';
import { ItemMasterApiService } from '../../../item-master/services/item-master-api.service';
import { CreateBomLineRequest, BomLineDto, EffectivityMethod } from '../../models/bom.model';
import { ItemMasterDto } from '../../../item-master/models/item-master.model';
import { debounceTime, distinctUntilChanged, Subject, switchMap } from 'rxjs';

@Component({
  selector: 'app-add-bom-line-form',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    ButtonModule, InputTextModule, InputNumberModule, SelectModule,
    DatePickerModule, MessageModule, AutoCompleteModule,
  ],
  template: `
    <div class="ablf">
      <div class="ablf__title">Add Component Line</div>

      @if (serverError) {
        <p-message severity="error" [text]="serverError" />
      }

      <form [formGroup]="form" class="ablf__form">
        <div class="ablf__row">

          <div class="ablf__field ablf__field--wide">
            <label>Component <span class="ablf__req">*</span></label>
            <p-autocomplete
              formControlName="componentSearch"
              [suggestions]="suggestions"
              field="partNumber"
              placeholder="Search part number..."
              [dropdown]="false"
              (completeMethod)="onSearch($event)"
              (onSelect)="onItemSelect($event)"
              [minLength]="1"
            >
              <ng-template #item let-item>
                <div class="ablf__suggestion">
                  <span class="ablf__suggestion-pn">{{ item.partNumber }}</span>
                  <span class="ablf__suggestion-rev">Rev {{ item.revision }}</span>
                  <span class="ablf__suggestion-desc">{{ item.description }}</span>
                </div>
              </ng-template>
            </p-autocomplete>
          </div>

          <div class="ablf__field">
            <label>Find #</label>
            <input pInputText formControlName="findNumber" placeholder="e.g. 10" />
          </div>

          <div class="ablf__field">
            <label>Qty <span class="ablf__req">*</span></label>
            <p-inputnumber formControlName="quantity" [min]="0.000001" [maxFractionDigits]="6" />
          </div>

          <div class="ablf__field">
            <label>Unit</label>
            <input pInputText formControlName="unitOfMeasure"
                   [attr.readonly]="true" placeholder="—"
                   title="Inherited from item master" class="ablf__uom-readonly" />
          </div>

          <div class="ablf__field">
            <label>Effectivity</label>
            <p-select formControlName="effectivityMethod" [options]="effectivityOptions"
                      optionLabel="label" optionValue="value" />
          </div>

          @if (effectivity === 'DATE') {
            <div class="ablf__field">
              <label>Eff From <span class="ablf__req">*</span></label>
              <p-datepicker formControlName="effectiveFromDate" dateFormat="yy-mm-dd" />
            </div>
            <div class="ablf__field">
              <label>Eff To</label>
              <p-datepicker formControlName="effectiveToDate" dateFormat="yy-mm-dd" />
            </div>
          }

          @if (effectivity === 'UNIT') {
            <div class="ablf__field">
              <label>Unit From <span class="ablf__req">*</span></label>
              <input pInputText formControlName="effectiveFromUnit" placeholder="e.g. SN-001" />
            </div>
            <div class="ablf__field">
              <label>Unit To</label>
              <input pInputText formControlName="effectiveToUnit" placeholder="e.g. SN-999" />
            </div>
          }

          <div class="ablf__field ablf__field--wide">
            <label>Ref Designators</label>
            <input pInputText formControlName="referenceDesignators" placeholder="e.g. C1, C2" />
          </div>

        </div>

        <div class="ablf__actions">
          <p-button label="Cancel" severity="secondary" size="small" (onClick)="cancel()" />
          <p-button label="Add Line" severity="primary" size="small"
                    [loading]="saving" [disabled]="!canSave()" (onClick)="save()" />
        </div>
      </form>
    </div>
  `,
  styles: [`
    .ablf {
      border: 1px solid var(--p-surface-border); border-radius: 8px;
      padding: 0.875rem; margin-bottom: 0.75rem; background: var(--p-surface-50);
    }
    .ablf__title { font-weight: 600; margin-bottom: 0.75rem; font-size: 0.9375rem; }
    .ablf__form { display: flex; flex-direction: column; gap: 0.75rem; }
    .ablf__row { display: flex; flex-wrap: wrap; gap: 0.75rem; }
    .ablf__field { display: flex; flex-direction: column; gap: 0.25rem; min-width: 140px; }
    .ablf__field--wide { min-width: 220px; flex: 1; }
    .ablf__req { color: #EF4444; }
    .ablf__actions { display: flex; gap: 0.5rem; justify-content: flex-end; }
    .ablf__suggestion { display: flex; align-items: baseline; gap: 0.5rem; }
    .ablf__suggestion-pn { font-weight: 600; font-size: 0.875rem; }
    .ablf__suggestion-rev { font-size: 0.75rem; color: var(--p-text-muted-color); }
    .ablf__suggestion-desc { font-size: 0.75rem; color: var(--p-text-muted-color); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 200px; }
    .ablf__uom-readonly { background: var(--p-surface-50); cursor: default; color: var(--p-text-muted-color); }
  `],
})
export class AddBomLineFormComponent implements OnInit {
  @Input() bomId = '';
  @Output() saved = new EventEmitter<BomLineDto>();
  @Output() cancelled = new EventEmitter<void>();

  private readonly bomApi     = inject(BomApiService);
  private readonly itemApi    = inject(ItemMasterApiService);
  private readonly fb         = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly cdr        = inject(ChangeDetectorRef);

  suggestions: ItemMasterDto[] = [];
  serverError = '';
  saving = false;
  selectedItemId = '';

  private readonly searchSubject = new Subject<string>();

  readonly effectivityOptions = [
    { label: 'None',  value: 'NONE' },
    { label: 'Date',  value: 'DATE' },
    { label: 'Unit',  value: 'UNIT' },
  ];

  form = this.fb.group({
    componentSearch: [null as ItemMasterDto | null],
    findNumber:         [''],
    quantity:           [null as number | null, Validators.required],
    unitOfMeasure:      [''],
    effectivityMethod:  ['NONE' as EffectivityMethod],
    effectiveFromDate:  [null as Date | null],
    effectiveToDate:    [null as Date | null],
    effectiveFromUnit:  [''],
    effectiveToUnit:    [''],
    referenceDesignators: [''],
  });

  get effectivity(): EffectivityMethod {
    return (this.form.get('effectivityMethod')?.value ?? 'NONE') as EffectivityMethod;
  }

  ngOnInit(): void {
    this.searchSubject.pipe(
      debounceTime(250),
      distinctUntilChanged(),
      switchMap(q => this.itemApi.list({ search: q, page: 0, size: 10 })),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(page => { this.suggestions = page.content; this.cdr.detectChanges(); });
  }

  onSearch(event: { query: string }): void {
    this.searchSubject.next(event.query);
  }

  onItemSelect(event: AutoCompleteSelectEvent): void {
    const item = event.value as ItemMasterDto;
    this.selectedItemId = item.id;
    this.form.patchValue({ unitOfMeasure: item.unitOfMeasure ?? '' });
  }

  canSave(): boolean {
    return !!this.selectedItemId && this.form.get('quantity')?.valid === true;
  }

  cancel(): void {
    this.cancelled.emit();
  }

  save(): void {
    if (!this.canSave()) return;
    this.saving = true;
    this.serverError = '';
    const v = this.form.value;
    const formatDate = (d: Date | null | undefined): string | undefined =>
      d ? d.toISOString().substring(0, 10) : undefined;

    const req: CreateBomLineRequest = {
      componentItemId: this.selectedItemId,
      quantity: v.quantity!,
      unitOfMeasure: v.unitOfMeasure || undefined,
      findNumber: v.findNumber || undefined,
      referenceDesignators: v.referenceDesignators || undefined,
      effectivityMethod: (v.effectivityMethod as EffectivityMethod) || undefined,
      effectiveFromDate: this.effectivity === 'DATE' ? formatDate(v.effectiveFromDate) : undefined,
      effectiveToDate: this.effectivity === 'DATE' ? formatDate(v.effectiveToDate) : undefined,
      effectiveFromUnit: this.effectivity === 'UNIT' ? (v.effectiveFromUnit || undefined) : undefined,
      effectiveToUnit: this.effectivity === 'UNIT' ? (v.effectiveToUnit || undefined) : undefined,
    };

    this.bomApi.addLine(this.bomId, req).subscribe({
      next: line => {
        this.saving = false;
        this.saved.emit(line);
      },
      error: (err: { status: number; error?: { message?: string; violations?: { field: string; message: string }[] } }) => {
        this.saving = false;
        if (err.status === 422 && err.error?.violations?.length) {
          this.serverError = err.error.violations.map(v => `${v.field}: ${v.message}`).join('; ');
        } else {
          this.serverError = err.error?.message ?? 'Failed to add line';
        }
        this.cdr.detectChanges();
      },
    });
  }
}
