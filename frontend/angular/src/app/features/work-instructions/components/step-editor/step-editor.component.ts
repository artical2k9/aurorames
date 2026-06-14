import {
  ChangeDetectorRef, Component, DestroyRef, EventEmitter, inject, Input, Output,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpEventType } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { ProgressBarModule } from 'primeng/progressbar';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { WorkInstructionApiService } from '../../services/work-instruction-api.service';
import { MediaAttachmentDto, StepDto } from '../../models/work-instruction.model';

/**
 * Step authoring for the editable DRAFT revision: add/edit/delete, reorder (up/down), and per-step
 * media upload (image/PDF/video) with progress, download, and delete. Body is authored as HTML and
 * sanitised server-side. Emits {@link changed} after any mutation so the parent reloads the WI.
 */
@Component({
  selector: 'app-step-editor',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TableModule, ButtonModule, DialogModule,
    InputTextModule, ProgressBarModule, ToastModule,
  ],
  providers: [MessageService],
  template: `
    <p-toast />
    <div class="se">
      <div class="se__bar">
        <h3 class="se__title">Steps ({{ steps.length }})</h3>
        @if (editable) {
          <p-button label="Add Step" icon="pi pi-plus" size="small" (onClick)="openAdd()" />
        }
      </div>

      @if (steps.length === 0) {
        <p class="se__empty">No steps yet.</p>
      }

      @for (step of orderedSteps(); track step.id; let i = $index) {
        <div class="se__step">
          <div class="se__step-head">
            <span class="se__step-no">{{ i + 1 }}</span>
            <span class="se__step-title">{{ step.title }}</span>
            <div class="se__step-actions">
              @if (editable) {
                <p-button [rounded]="true" [text]="true" size="small" icon="pi pi-arrow-up"
                          title="Move up" [disabled]="i === 0" (onClick)="moveUp(i)" />
                <p-button [rounded]="true" [text]="true" size="small" icon="pi pi-arrow-down"
                          title="Move down" [disabled]="i === steps.length - 1"
                          (onClick)="moveDown(i)" />
                <p-button [rounded]="true" [text]="true" size="small" icon="pi pi-pencil"
                          title="Edit" (onClick)="openEdit(step)" />
                <p-button [rounded]="true" [text]="true" size="small" icon="pi pi-trash"
                          severity="danger" title="Delete" (onClick)="remove(step)" />
              }
            </div>
          </div>
          @if (step.bodyHtml) {
            <div class="se__body" [innerHTML]="step.bodyHtml"></div>
          }

          <div class="se__media">
            @for (m of step.media; track m.id) {
              <div class="se__media-item">
                <span class="se__media-name">{{ m.fileName }}</span>
                <p-button [rounded]="true" [text]="true" size="small" icon="pi pi-download"
                          title="Download" (onClick)="download(m)" />
                @if (editable) {
                  <p-button [rounded]="true" [text]="true" size="small" icon="pi pi-times"
                            severity="danger" title="Remove" (onClick)="removeMedia(step, m)" />
                }
              </div>
            }
            @if (editable) {
              <label class="se__upload">
                <input type="file" hidden
                       accept="image/png,image/jpeg,application/pdf,video/mp4"
                       (change)="onFileSelected(step, $event)" />
                <span class="se__upload-btn">+ Attach media</span>
              </label>
              @if (uploadingStepId === step.id) {
                <p-progressBar [value]="uploadProgress" [style]="{ height: '6px' }" />
              }
            }
          </div>
        </div>
      }

      <p-dialog [header]="editing ? 'Edit Step' : 'Add Step'" [(visible)]="showDialog"
                [modal]="true" [style]="{ width: '560px' }">
        <div class="se__form">
          <label>Title *</label>
          <input pInputText [(ngModel)]="draft.title" />
          <label>Body (HTML — sanitised on save)</label>
          <textarea pInputText rows="6" [(ngModel)]="draft.bodyHtml"
                    placeholder="<p>Step instructions…</p>"></textarea>
          @if (serverError) { <small class="se__error">{{ serverError }}</small> }
        </div>
        <ng-template pTemplate="footer">
          <p-button label="Cancel" severity="secondary" size="small" (onClick)="showDialog = false" />
          <p-button label="Save" severity="primary" size="small"
                    [loading]="saving" [disabled]="!draft.title" (onClick)="save()" />
        </ng-template>
      </p-dialog>
    </div>
  `,
  styles: [`
    .se { margin-top: 1rem; }
    .se__bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 0.5rem; }
    .se__title { margin: 0; font-size: 1.05rem; font-weight: 600; }
    .se__empty { color: var(--p-text-muted-color); }
    .se__step { border: 1px solid var(--p-content-border-color); border-radius: 6px;
      padding: 0.75rem; margin-bottom: 0.625rem; }
    .se__step-head { display: flex; align-items: center; gap: 0.5rem; }
    .se__step-no { display: inline-flex; align-items: center; justify-content: center;
      width: 1.5rem; height: 1.5rem; border-radius: 50%; background: var(--p-primary-color);
      color: var(--p-primary-contrast-color); font-size: 0.75rem; font-weight: 700; }
    .se__step-title { font-weight: 600; flex: 1; }
    .se__step-actions { display: flex; gap: 0.125rem; }
    .se__body { margin: 0.5rem 0 0 2rem; font-size: 0.875rem; }
    .se__media { display: flex; flex-wrap: wrap; align-items: center; gap: 0.5rem;
      margin: 0.5rem 0 0 2rem; }
    .se__media-item { display: flex; align-items: center; gap: 0.25rem;
      background: var(--p-content-hover-background); border-radius: 4px; padding: 0.125rem 0.5rem;
      font-size: 0.8125rem; }
    .se__upload { cursor: pointer; }
    .se__upload-btn { font-size: 0.8125rem; color: var(--p-primary-color); font-weight: 600; }
    .se__form { display: flex; flex-direction: column; gap: 0.375rem; }
    .se__form label { font-size: 0.8125rem; font-weight: 600; margin-top: 0.375rem; }
    .se__error { color: var(--p-red-500); }
  `],
})
export class StepEditorComponent {
  private readonly api            = inject(WorkInstructionApiService);
  private readonly messageService = inject(MessageService);
  private readonly destroyRef     = inject(DestroyRef);
  private readonly cdr            = inject(ChangeDetectorRef);

  @Input() workInstructionId = '';
  @Input() steps: StepDto[] = [];
  @Input() editable = false;
  @Output() changed = new EventEmitter<void>();

  showDialog = false;
  editing = false;
  saving = false;
  serverError = '';
  draft: { title?: string; bodyHtml?: string } = {};
  private editId: string | null = null;

  uploadingStepId: string | null = null;
  uploadProgress = 0;

  orderedSteps(): StepDto[] {
    return [...this.steps].sort((a, b) => a.stepNumber - b.stepNumber);
  }

  openAdd(): void {
    this.editing = false;
    this.editId = null;
    this.serverError = '';
    this.draft = {};
    this.showDialog = true;
  }

  openEdit(step: StepDto): void {
    this.editing = true;
    this.editId = step.id;
    this.serverError = '';
    this.draft = { title: step.title, bodyHtml: step.bodyHtml };
    this.showDialog = true;
  }

  save(): void {
    if (!this.draft.title) return;
    this.saving = true;
    this.serverError = '';
    const req = { title: this.draft.title, bodyHtml: this.draft.bodyHtml };
    const obs = this.editing && this.editId
      ? this.api.updateStep(this.workInstructionId, this.editId, req)
      : this.api.addStep(this.workInstructionId, req);
    obs.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.saving = false;
        this.showDialog = false;
        this.changed.emit();
        this.cdr.detectChanges();
      },
      error: err => {
        this.saving = false;
        this.serverError = this.extractError(err);
        this.cdr.detectChanges();
      },
    });
  }

  remove(step: StepDto): void {
    this.api.deleteStep(this.workInstructionId, step.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => { this.changed.emit(); this.cdr.detectChanges(); },
        error: err => this.toastError(err),
      });
  }

  moveUp(index: number): void {
    if (index === 0) return;
    this.reorderSwap(index, index - 1);
  }

  moveDown(index: number): void {
    if (index >= this.steps.length - 1) return;
    this.reorderSwap(index, index + 1);
  }

  private reorderSwap(a: number, b: number): void {
    const ordered = this.orderedSteps().map(s => s.id);
    [ordered[a], ordered[b]] = [ordered[b], ordered[a]];
    this.api.reorderSteps(this.workInstructionId, ordered)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => { this.changed.emit(); this.cdr.detectChanges(); },
        error: err => this.toastError(err),
      });
  }

  onFileSelected(step: StepDto, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.uploadingStepId = step.id;
    this.uploadProgress = 0;
    this.api.uploadMedia(this.workInstructionId, step.id, file)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ev => {
          if (ev.type === HttpEventType.UploadProgress && ev.total) {
            this.uploadProgress = Math.round((ev.loaded / ev.total) * 100);
            this.cdr.detectChanges();
          } else if (ev.type === HttpEventType.Response) {
            this.uploadingStepId = null;
            input.value = '';
            this.changed.emit();
            this.cdr.detectChanges();
          }
        },
        error: err => {
          this.uploadingStepId = null;
          input.value = '';
          this.toastError(err);
        },
      });
  }

  download(media: MediaAttachmentDto): void {
    this.api.downloadMedia(media.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = media.fileName;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: err => this.toastError(err),
    });
  }

  removeMedia(step: StepDto, media: MediaAttachmentDto): void {
    this.api.deleteMedia(this.workInstructionId, step.id, media.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => { this.changed.emit(); this.cdr.detectChanges(); },
        error: err => this.toastError(err),
      });
  }

  private toastError(err: unknown): void {
    this.messageService.add({ severity: 'error', summary: this.extractError(err) });
    this.cdr.detectChanges();
  }

  private extractError(err: unknown): string {
    const e = err as { error?: { error?: string; details?: string[] } };
    if (e?.error?.details?.length) return e.error.details.join('; ');
    return e?.error?.error ?? 'Operation failed';
  }
}
