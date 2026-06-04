import {
  Component, inject, Input, OnChanges, OnInit, SimpleChanges,
} from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { SelectModule } from 'primeng/select';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { SkeletonModule } from 'primeng/skeleton';
import { UdfApiService, UdfFieldDefinition } from './udf-api.service';

/**
 * Drop-in UDF form section. Add to any form that needs custom fields:
 *
 *   <app-udf-fields
 *     moduleKey="ITEM_MASTER"
 *     [udfGroup]="form.get('udfValues')"
 *     [initialValues]="item?.customFields ?? {}"
 *   />
 *
 * The component fetches field definitions, adds reactive form controls to
 * the provided udfGroup, and renders the appropriate input for each field type.
 * initialValues are applied on mount and re-applied whenever they change
 * (handles dialogs re-opened for different records).
 */
@Component({
  selector: 'app-udf-fields',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    ButtonModule, InputTextModule, InputNumberModule,
    SelectModule, ToggleSwitchModule, SkeletonModule,
  ],
  template: `
    @if (loading) {
      <div class="udf__skeleton">
        @for (i of [1, 2]; track i) { <p-skeleton height="2rem" /> }
      </div>
    } @else if (fields.length > 0) {
      <div class="udf__section">
        <h3 class="udf__section-title">Custom Fields</h3>
        <p class="udf__section-sub">
          {{ fields.length }} field{{ fields.length !== 1 ? 's' : '' }} configured for this module
        </p>
        <div class="udf__grid" [formGroup]="udfGroup">
          @for (field of fields; track field.fieldKey) {
            <div class="udf__field">
              <label class="udf__label">
                {{ field.label }}
                @if (field.required) { <span class="udf__req">*</span> }
              </label>
              @switch (field.fieldType) {
                @case ('NUMBER') {
                  <p-inputnumber [formControlName]="field.fieldKey" />
                }
                @case ('BOOLEAN') {
                  <p-toggleswitch [formControlName]="field.fieldKey" />
                }
                @case ('LIST') {
                  <p-select [formControlName]="field.fieldKey"
                            [options]="field.listOptions ?? []"
                            placeholder="Select…" />
                }
                @default {
                  <input pInputText [formControlName]="field.fieldKey" />
                }
              }
            </div>
          }
        </div>
      </div>
    }
  `,
  styles: [`
    .udf__skeleton { display: flex; flex-direction: column; gap: 0.5rem; }

    .udf__section {
      border: 1px solid var(--p-surface-border);
      border-radius: 8px;
      padding: 1.25rem;
    }
    .udf__section-title { margin: 0 0 0.25rem; font-size: 0.9375rem; font-weight: 600; }
    .udf__section-sub {
      font-size: 0.8125rem; color: var(--p-text-muted-color);
      margin: 0 0 1rem;
    }

    .udf__grid {
      display: grid; grid-template-columns: repeat(2, 1fr); gap: 0.875rem;
    }
    .udf__field { display: flex; flex-direction: column; gap: 0.3rem; }
    .udf__label {
      font-size: 0.8125rem; font-weight: 500; color: var(--p-text-muted-color);
    }
    .udf__req { color: #EF4444; }
  `],
})
export class UdfFieldsComponent implements OnInit, OnChanges {
  private readonly udfApi = inject(UdfApiService);
  private readonly fb    = inject(FormBuilder);

  @Input() moduleKey!: string;
  @Input() udfGroup!: FormGroup;
  @Input() initialValues: Record<string, unknown> = {};

  fields: UdfFieldDefinition[] = [];
  loading = false;

  ngOnInit(): void {
    this.loading = true;
    this.udfApi.listFields(this.moduleKey).subscribe({
      next: fields => {
        this.fields = fields;
        this.loading = false;
        this.syncControls();
      },
      error: () => { this.loading = false; },
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    // Re-patch values when initialValues arrive after the fields are already loaded.
    // firstChange is skipped because ngOnInit handles the initial population.
    if (changes['initialValues'] && !changes['initialValues'].firstChange && this.fields.length > 0) {
      this.patchValues();
    }
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private syncControls(): void {
    this.fields.forEach(f => {
      const initialVal = this.initialValues[f.fieldKey] ?? f.defaultValue ?? null;
      if (this.udfGroup.contains(f.fieldKey)) {
        // Control persists from a previous dialog open — update its value.
        this.udfGroup.get(f.fieldKey)!.setValue(initialVal);
      } else {
        this.udfGroup.addControl(
          f.fieldKey,
          this.fb.control(initialVal, f.required ? Validators.required : []),
        );
      }
    });
  }

  private patchValues(): void {
    this.fields.forEach(f => {
      const val = this.initialValues[f.fieldKey];
      if (val !== undefined) {
        this.udfGroup.get(f.fieldKey)?.setValue(val);
      }
    });
  }
}
