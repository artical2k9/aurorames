import {
  AfterViewInit, ChangeDetectorRef, Component, DestroyRef, inject,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { BreadcrumbService } from '../../../../shared/ui';
import { WorkInstructionApiService } from '../../services/work-instruction-api.service';
import {
  RevisionStatus, RevisionSummaryDto, WorkInstructionDto,
} from '../../models/work-instruction.model';
import { StepEditorComponent } from '../../components/step-editor/step-editor.component';
import { SignatureDialogComponent } from '../../components/signature-dialog/signature-dialog.component';
import {
  SkillRequirementsPanelComponent,
} from '../../components/skill-requirements-panel/skill-requirements-panel.component';

@Component({
  selector: 'app-work-instruction-detail',
  standalone: true,
  imports: [
    CommonModule, FormsModule, ButtonModule, SelectModule, TagModule,
    DialogModule, InputTextModule, ToastModule,
    StepEditorComponent, SignatureDialogComponent, SkillRequirementsPanelComponent,
  ],
  providers: [MessageService],
  template: `
    <p-toast />
    @if (wi) {
      <div class="wid">
        <div class="wid__heading">
          <div>
            <h2 class="wid__title">{{ wi.identifier }} — {{ wi.title }}</h2>
            <p-tag [value]="statusLabel(wi.revisionStatus)"
                   [severity]="statusSeverity(wi.revisionStatus)" />
          </div>
          <div class="wid__actions">
            <p-select [options]="revisionOptions" [(ngModel)]="selectedRevision"
                      optionLabel="label" optionValue="value"
                      (onChange)="onRevisionChange()" styleClass="wid__rev" />
            @if (wi.revisionStatus === 'DRAFT' && isLatest()) {
              <p-button label="Submit" size="small" severity="primary"
                        (onClick)="submit()" [loading]="acting" />
              <p-button label="Cancel Draft" size="small" severity="danger" [outlined]="true"
                        (onClick)="cancelDraft()" [loading]="acting" />
            }
            @if (wi.revisionStatus === 'PENDING_APPROVAL' && isLatest()) {
              <p-button label="Approve" size="small" severity="success"
                        (onClick)="openApprove()" [loading]="acting" />
              <p-button label="Reject" size="small" severity="danger"
                        (onClick)="openReject()" [loading]="acting" />
            }
            @if (wi.revisionStatus === 'APPROVED' && !wi.hasDraft && isLatest()) {
              <p-button label="Create Revision" size="small" severity="secondary"
                        (onClick)="createRevision()" [loading]="acting" />
            }
          </div>
        </div>

        @if (wi.description) { <p class="wid__desc">{{ wi.description }}</p> }
        @if (wi.rejectionReason && wi.revisionStatus === 'DRAFT') {
          <p class="wid__reject">Last rejection: {{ wi.rejectionReason }}</p>
        }

        <app-step-editor
          [workInstructionId]="wi.id"
          [steps]="wi.steps"
          [editable]="isEditable()"
          (changed)="reloadCurrentRevision()" />

        <app-skill-requirements-panel
          [workInstructionId]="wi.id"
          [revisionNumber]="viewingRevisionNumber"
          [editable]="isEditable()"
          (changed)="reload()" />

        @if (wi.signatures.length) {
          <div class="wid__sigs">
            <h3 class="wid__sigs-title">Electronic Signatures</h3>
            <ul>
              @for (s of wi.signatures; track s.id) {
                <li>{{ s.meaning }} — {{ s.signerFullName }} ({{ s.signedAt | date:'medium' }})</li>
              }
            </ul>
          </div>
        }
      </div>

      <app-signature-dialog
        [(visible)]="showApprove"
        [workInstructionId]="wi.id"
        meaning="Approval of work instruction"
        (approved)="onApproved()" />

      <p-dialog header="Reject Revision" [(visible)]="showReject" [modal]="true"
                [style]="{ width: '420px' }">
        <div class="wid__form">
          <label>Reason *</label>
          <input pInputText [(ngModel)]="rejectReason" />
        </div>
        <ng-template pTemplate="footer">
          <p-button label="Cancel" severity="secondary" size="small" (onClick)="showReject = false" />
          <p-button label="Reject" severity="danger" size="small"
                    [disabled]="!rejectReason" [loading]="acting" (onClick)="reject()" />
        </ng-template>
      </p-dialog>
    }
  `,
  styles: [`
    .wid { padding: 1.25rem; }
    .wid__heading { display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem; }
    .wid__title { margin: 0 0 0.5rem; font-size: 1.375rem; font-weight: 700; }
    .wid__actions { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; }
    :host ::ng-deep .wid__rev { min-width: 220px; }
    .wid__desc { color: var(--p-text-muted-color); margin: 0.5rem 0 0; }
    .wid__reject { color: var(--p-orange-500); font-size: 0.875rem; margin: 0.5rem 0 0; }
    .wid__sigs { margin-top: 1.25rem; }
    .wid__sigs-title { margin: 0 0 0.375rem; font-size: 1.05rem; font-weight: 600; }
    .wid__sigs ul { margin: 0; padding-left: 1.25rem; font-size: 0.875rem; }
    .wid__form { display: flex; flex-direction: column; gap: 0.375rem; }
    .wid__form label { font-size: 0.8125rem; font-weight: 600; }
  `],
})
export class WorkInstructionDetailComponent implements AfterViewInit {
  private readonly api            = inject(WorkInstructionApiService);
  private readonly route          = inject(ActivatedRoute);
  private readonly router         = inject(Router);
  private readonly messageService = inject(MessageService);
  private readonly destroyRef     = inject(DestroyRef);
  private readonly cdr            = inject(ChangeDetectorRef);
  private readonly breadcrumbSvc  = inject(BreadcrumbService);

  wiId = '';
  wi: WorkInstructionDto | null = null;
  revisionOptions: { label: string; value: number }[] = [];
  selectedRevision: number | null = null;
  viewingRevisionNumber?: number;
  private latestRevision?: number;

  acting = false;
  showApprove = false;
  showReject = false;
  rejectReason = '';

  ngAfterViewInit(): void {
    this.wiId = this.route.snapshot.paramMap.get('id') ?? '';
    setTimeout(() => this.reload());
  }

  reload(): void {
    this.api.get(this.wiId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: wi => {
        this.wi = wi;
        this.latestRevision = wi.revision;
        this.viewingRevisionNumber = wi.revision;
        this.selectedRevision = wi.revision;
        this.breadcrumbSvc.set([
          { label: 'Engineering' },
          { label: 'Work Instructions', route: ['/engineering/work-instructions'] },
          { label: wi.identifier },
        ]);
        this.loadRevisions();
        this.cdr.detectChanges();
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Failed to load work instruction' });
        this.cdr.detectChanges();
      },
    });
  }

  /** Reload only the currently-viewed revision (used after step/media edits). */
  reloadCurrentRevision(): void {
    this.api.get(this.wiId, { revisionNumber: this.viewingRevisionNumber })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: wi => { this.wi = wi; this.cdr.detectChanges(); },
      });
  }

  private loadRevisions(): void {
    this.api.listRevisions(this.wiId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (revs: RevisionSummaryDto[]) => {
        this.revisionOptions = revs
          .sort((a, b) => b.revision - a.revision)
          .map(r => ({
            label: `Rev ${r.revision} (${this.statusLabel(r.revisionStatus)})`,
            value: r.revision,
          }));
        this.cdr.detectChanges();
      },
    });
  }

  onRevisionChange(): void {
    this.viewingRevisionNumber = this.selectedRevision ?? this.latestRevision;
    if (this.selectedRevision != null) {
      this.api.get(this.wiId, { revisionNumber: this.selectedRevision })
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: wi => { this.wi = wi; this.cdr.detectChanges(); },
        });
    }
  }

  isLatest(): boolean {
    return this.viewingRevisionNumber === this.latestRevision;
  }

  isEditable(): boolean {
    return this.wi?.revisionStatus === 'DRAFT' && this.isLatest();
  }

  statusLabel(status: RevisionStatus): string {
    switch (status) {
      case 'APPROVED': return 'Approved';
      case 'PENDING_APPROVAL': return 'Pending';
      default: return 'Draft';
    }
  }

  statusSeverity(status: RevisionStatus): 'success' | 'warn' | 'secondary' {
    switch (status) {
      case 'APPROVED': return 'success';
      case 'PENDING_APPROVAL': return 'warn';
      default: return 'secondary';
    }
  }

  submit(): void {
    this.runAction(this.api.submit(this.wiId), 'Submitted for approval');
  }

  openApprove(): void {
    this.showApprove = true;
  }

  onApproved(): void {
    this.messageService.add({ severity: 'success', summary: 'Work instruction approved' });
    this.reload();
  }

  createRevision(): void {
    this.runAction(this.api.createRevision(this.wiId), 'Draft revision created');
  }

  cancelDraft(): void {
    this.acting = true;
    this.api.cancelDraft(this.wiId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.acting = false;
        this.messageService.add({ severity: 'success', summary: 'Draft cancelled' });
        this.cdr.detectChanges();
        this.router.navigate(['/engineering/work-instructions']);
      },
      error: err => this.actionError(err),
    });
  }

  openReject(): void {
    this.rejectReason = '';
    this.showReject = true;
  }

  reject(): void {
    this.acting = true;
    this.api.reject(this.wiId, this.rejectReason)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.acting = false;
          this.showReject = false;
          this.messageService.add({ severity: 'success', summary: 'Revision rejected' });
          this.cdr.detectChanges();
          this.reload();
        },
        error: err => this.actionError(err),
      });
  }

  private runAction(obs: Observable<WorkInstructionDto>, success: string): void {
    this.acting = true;
    obs.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.acting = false;
        this.messageService.add({ severity: 'success', summary: success });
        this.cdr.detectChanges();
        this.reload();
      },
      error: err => this.actionError(err),
    });
  }

  private actionError(err: unknown): void {
    this.acting = false;
    const e = err as { error?: { error?: string; details?: string[] } };
    const msg = e?.error?.details?.length
      ? e.error.details.join('; ')
      : (e?.error?.error ?? 'Action failed');
    this.messageService.add({ severity: 'error', summary: msg });
    this.cdr.detectChanges();
  }
}
