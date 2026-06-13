import {
  ChangeDetectorRef, Component, DestroyRef, EventEmitter, inject, Input, OnInit, Output,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { DatePickerModule } from 'primeng/datepicker';
import { LabourApiService } from '../../services/labour-api.service';
import { CertificationDto, SkillDto } from '../../models/labour.model';

@Component({
  selector: 'app-award-certification-dialog',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    DialogModule, ButtonModule, InputTextModule, SelectModule, DatePickerModule,
  ],
  template: `
    <p-dialog header="Award Certification" [visible]="visible" [modal]="true"
              (visibleChange)="visibleChange.emit($event)"
              [style]="{ width: '440px' }">
      <div class="acd__form">
        <label>Skill *</label>
        <p-select [options]="skills" optionLabel="label" optionValue="value"
                  [(ngModel)]="skillId" placeholder="Select skill" [filter]="true" />
        <label>Award Date *</label>
        <p-datepicker [(ngModel)]="awardDate" dateFormat="yy-mm-dd" [showIcon]="true" />
        <label>Expiry Date (defaults from skill validity)</label>
        <p-datepicker [(ngModel)]="expiryDate" dateFormat="yy-mm-dd" [showIcon]="true" />
        <label>Assessor</label>
        <input pInputText [(ngModel)]="assessor" />
        <label>Evidence Reference</label>
        <input pInputText [(ngModel)]="evidenceRef" />
        @if (serverError) {
          <small class="acd__error">{{ serverError }}</small>
        }
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancel" severity="secondary" size="small"
                  (onClick)="visibleChange.emit(false)" />
        <p-button label="Award" severity="primary" size="small"
                  [loading]="saving" [disabled]="!skillId || !awardDate"
                  (onClick)="award()" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .acd__form { display: flex; flex-direction: column; gap: 0.375rem; }
    .acd__form label { font-size: 0.8125rem; font-weight: 600; margin-top: 0.375rem; }
    .acd__error { color: var(--p-red-500); }
  `],
})
export class AwardCertificationDialogComponent implements OnInit {
  private readonly api = inject(LabourApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly cdr = inject(ChangeDetectorRef);

  @Input() visible = false;
  @Input({ required: true }) employeeId = '';
  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() awarded = new EventEmitter<CertificationDto>();

  skills: { label: string; value: string }[] = [];
  skillId = '';
  awardDate: Date | null = new Date();
  expiryDate: Date | null = null;
  assessor = '';
  evidenceRef = '';
  saving = false;
  serverError = '';

  ngOnInit(): void {
    this.api.listSkills({ active: true, size: 200 })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: page => {
          this.skills = page.content.map((s: SkillDto) => ({
            label: `${s.skillCode} — ${s.name}`,
            value: s.id,
          }));
          this.cdr.detectChanges();
        },
        error: () => {
          this.skills = [];
          this.cdr.detectChanges();
        },
      });
  }

  award(): void {
    if (!this.skillId || !this.awardDate) return;
    this.saving = true;
    this.serverError = '';
    this.api.awardCertification({
      employeeId: this.employeeId,
      skillId: this.skillId,
      awardDate: toIsoDate(this.awardDate),
      expiryDate: this.expiryDate ? toIsoDate(this.expiryDate) : undefined,
      assessor: this.assessor || undefined,
      evidenceRef: this.evidenceRef || undefined,
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: cert => {
        this.saving = false;
        this.visibleChange.emit(false);
        this.awarded.emit(cert);
        this.cdr.detectChanges();
      },
      error: err => {
        this.saving = false;
        this.serverError = err?.error?.error ?? 'Failed to award certification';
        this.cdr.detectChanges();
      },
    });
  }
}

function toIsoDate(d: Date): string {
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${month}-${day}`;
}
