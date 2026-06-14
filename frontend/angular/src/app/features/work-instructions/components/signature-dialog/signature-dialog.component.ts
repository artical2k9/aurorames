import {
  ChangeDetectorRef, Component, DestroyRef, EventEmitter, inject, Input, Output,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { WorkInstructionApiService } from '../../services/work-instruction-api.service';
import { WorkInstructionDto } from '../../models/work-instruction.model';

/**
 * 21 CFR Part 11 e-signature dialog: re-authenticates the approver with their password before
 * applying an APPROVED signature. The password is sent only to the approve endpoint (verified
 * against Keycloak and discarded). Displays the 422 verification-failed error inline.
 */
@Component({
  selector: 'app-signature-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, DialogModule, ButtonModule, InputTextModule],
  template: `
    <p-dialog header="Electronic Signature" [visible]="visible" [modal]="true"
              (visibleChange)="onDialogVisible($event)" [style]="{ width: '440px' }"
              (onHide)="reset()">
      <div class="sig">
        <p class="sig__meaning">
          By signing you confirm the meaning: <strong>{{ meaning }}</strong>.
          This applies an immutable electronic signature (21 CFR Part 11).
        </p>
        <label for="sig-pw">Re-enter your password *</label>
        <input id="sig-pw" pInputText type="password" autocomplete="off"
               [(ngModel)]="password" (keyup.enter)="confirm()" />
        @if (error) { <small class="sig__error">{{ error }}</small> }
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancel" severity="secondary" size="small" (onClick)="cancel()" />
        <p-button label="Sign & Approve" severity="success" size="small"
                  [loading]="submitting" [disabled]="!password" (onClick)="confirm()" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .sig { display: flex; flex-direction: column; gap: 0.5rem; }
    .sig__meaning { margin: 0 0 0.25rem; font-size: 0.875rem; color: var(--p-text-muted-color); }
    .sig label { font-size: 0.8125rem; font-weight: 600; }
    .sig__error { color: var(--p-red-500); }
  `],
})
export class SignatureDialogComponent {
  private readonly api        = inject(WorkInstructionApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly cdr        = inject(ChangeDetectorRef);

  @Input() visible = false;
  @Input() workInstructionId = '';
  @Input() meaning = 'Approval';
  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() approved = new EventEmitter<WorkInstructionDto>();

  password = '';
  error = '';
  submitting = false;

  confirm(): void {
    if (!this.password) return;
    this.submitting = true;
    this.error = '';
    this.api.approve(this.workInstructionId, this.password)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: dto => {
          this.submitting = false;
          this.approved.emit(dto);
          this.close();
          this.cdr.detectChanges();
        },
        error: err => {
          this.submitting = false;
          this.error = this.extractError(err);
          this.cdr.detectChanges();
        },
      });
  }

  cancel(): void {
    this.close();
  }

  onDialogVisible(visible: boolean): void {
    this.visible = visible;
    this.visibleChange.emit(visible);
  }

  reset(): void {
    this.password = '';
    this.error = '';
    this.submitting = false;
  }

  private close(): void {
    this.reset();
    this.visible = false;
    this.visibleChange.emit(false);
  }

  private extractError(err: unknown): string {
    const e = err as { status?: number; error?: { error?: string; details?: string[] } };
    if (e?.error?.details?.length) return e.error.details.join('; ');
    if (e?.error?.error) return e.error.error;
    if (e?.status === 422 || e?.status === 401) return 'Password verification failed';
    return 'Signature failed';
  }
}
