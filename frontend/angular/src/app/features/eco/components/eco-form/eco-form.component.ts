import { ChangeDetectorRef, Component, EventEmitter, inject, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { MultiSelectModule } from 'primeng/multiselect';
import { MessageModule } from 'primeng/message';
import { EcoApiService } from '../../services/eco-api.service';
import { ItemMasterApiService } from '../../../item-master/services/item-master-api.service';
import { EcoDto, CreateEcoRequest } from '../../models/eco.model';
import { debounceTime, distinctUntilChanged, Subject, switchMap } from 'rxjs';

@Component({
  selector: 'app-eco-form',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    DialogModule, ButtonModule, InputTextModule, TextareaModule,
    MultiSelectModule, MessageModule,
  ],
  template: `
    <p-dialog header="New Engineering Change Order" [(visible)]="visible"
              [modal]="true" [style]="{ width: '540px' }"
              (visibleChange)="visibleChange.emit($event)">

      @if (serverError) {
        <p-message severity="error" [text]="serverError" />
      }

      <form [formGroup]="form" class="ef__form">

        <div class="ef__field">
          <label>Title <span class="ef__req">*</span></label>
          <input pInputText formControlName="title" />
        </div>

        <div class="ef__field">
          <label>Description</label>
          <textarea pTextarea formControlName="description" rows="3"></textarea>
        </div>

        <div class="ef__field">
          <label>Affected Item Masters</label>
          <p-multiselect
            [options]="itemOptions"
            formControlName="affectedItemIds"
            optionLabel="label"
            optionValue="value"
            placeholder="Search and select items..."
            [filter]="true"
            (onFilter)="onFilter($event)"
            display="chip"
          />
        </div>

      </form>

      <ng-template pTemplate="footer">
        <p-button label="Cancel" severity="secondary" size="small"
                  (onClick)="close()" />
        <p-button label="Save ECO" severity="primary" size="small"
                  [loading]="saving" [disabled]="form.invalid"
                  (onClick)="save()" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .ef__form { display: flex; flex-direction: column; gap: 0.75rem; }
    .ef__field { display: flex; flex-direction: column; gap: 0.25rem; }
    .ef__req { color: #EF4444; }
  `],
})
export class EcoFormComponent {
  @Input() visible = false;
  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() saved = new EventEmitter<EcoDto>();

  private readonly ecoApi = inject(EcoApiService);
  private readonly itemApi = inject(ItemMasterApiService);
  private readonly fb     = inject(FormBuilder);
  private readonly cdr    = inject(ChangeDetectorRef);

  itemOptions: { label: string; value: string }[] = [];
  saving = false;
  serverError = '';

  private readonly filterSubject = new Subject<string>();

  form = this.fb.group({
    title:            ['', Validators.required],
    description:      [''],
    affectedItemIds:  [[] as string[]],
  });

  constructor() {
    this.filterSubject.pipe(
      debounceTime(250),
      distinctUntilChanged(),
      switchMap(q => this.itemApi.list({ search: q, page: 0, size: 15 })),
    ).subscribe(page => {
      this.itemOptions = page.content.map(i => ({
        label: `${i.partNumber} Rev ${i.revision}`,
        value: i.id,
      }));
      this.cdr.detectChanges();
    });
    this.itemApi.list({ page: 0, size: 15 }).subscribe(page => {
      this.itemOptions = page.content.map(i => ({
        label: `${i.partNumber} Rev ${i.revision}`,
        value: i.id,
      }));
      this.cdr.detectChanges();
    });
  }

  onFilter(event: { filter: string }): void {
    this.filterSubject.next(event.filter ?? '');
  }

  close(): void {
    this.form.reset({ title: '', description: '', affectedItemIds: [] });
    this.serverError = '';
    this.visibleChange.emit(false);
  }

  save(): void {
    if (this.form.invalid) return;
    this.saving = true;
    this.serverError = '';
    const v = this.form.value;
    const req: CreateEcoRequest = {
      title: v.title!,
      description: v.description || undefined,
      affectedItemIds: v.affectedItemIds ?? [],
    };
    this.ecoApi.create(req).subscribe({
      next: eco => {
        this.saving = false;
        this.form.reset({ title: '', description: '', affectedItemIds: [] });
        this.saved.emit(eco);
      },
      error: (err: { error?: { message?: string } }) => {
        this.saving = false;
        this.serverError = err.error?.message ?? 'Failed to create ECO';
        this.cdr.detectChanges();
      },
    });
  }
}
