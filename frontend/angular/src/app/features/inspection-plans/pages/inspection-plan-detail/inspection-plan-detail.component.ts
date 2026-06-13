import {
  AfterViewInit, ChangeDetectorRef, Component, DestroyRef, inject,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { BreadcrumbService } from '../../../../shared/ui';
import { InspectionPlanApiService } from '../../services/inspection-plan-api.service';
import {
  InspectionPlanDto, RevisionStatus, RevisionSummaryDto,
} from '../../models/inspection-plan.model';
import { CharacteristicEditorComponent } from '../../components/characteristic-editor/characteristic-editor.component';

@Component({
  selector: 'app-inspection-plan-detail',
  standalone: true,
  imports: [
    CommonModule, FormsModule, ButtonModule, SelectModule, TagModule,
    DialogModule, InputTextModule, ToastModule, CharacteristicEditorComponent,
  ],
  providers: [MessageService],
  template: `
    <p-toast />
    @if (plan) {
      <div class="ipd">
        <div class="ipd__heading">
          <div>
            <h2 class="ipd__title">{{ plan.partNumber }} — {{ plan.name }}</h2>
            <p-tag [value]="statusLabel(plan.revisionStatus)"
                   [severity]="statusSeverity(plan.revisionStatus)" />
          </div>
          <div class="ipd__actions">
            <p-select [options]="revisionOptions" [(ngModel)]="selectedRevision"
                      optionLabel="label" optionValue="value"
                      (onChange)="onRevisionChange()" styleClass="ipd__rev" />
            @if (plan.revisionStatus === 'DRAFT') {
              <p-button label="Submit" size="small" severity="primary"
                        (onClick)="submit()" [loading]="acting" />
              <p-button label="Cancel Draft" size="small" severity="danger" [outlined]="true"
                        (onClick)="cancelDraft()" [loading]="acting" />
            }
            @if (plan.revisionStatus === 'PENDING_APPROVAL') {
              <p-button label="Approve" size="small" severity="success"
                        (onClick)="approve()" [loading]="acting" />
              <p-button label="Reject" size="small" severity="danger"
                        (onClick)="openReject()" [loading]="acting" />
            }
            @if (plan.revisionStatus === 'APPROVED' && !plan.hasDraft) {
              <p-button label="Create Revision" size="small" severity="secondary"
                        (onClick)="createRevision()" [loading]="acting" />
            }
          </div>
        </div>

        @if (plan.description) { <p class="ipd__desc">{{ plan.description }}</p> }

        <app-characteristic-editor
          [planId]="plan.id"
          [revisionNumber]="viewingRevisionNumber"
          [editable]="plan.revisionStatus === 'DRAFT' && viewingRevisionNumber === plan.revision" />
      </div>

      <p-dialog header="Reject Revision" [(visible)]="showReject" [modal]="true"
                [style]="{ width: '420px' }">
        <div class="ipd__form">
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
    .ipd { padding: 1.25rem; }
    .ipd__heading { display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem; }
    .ipd__title { margin: 0 0 0.5rem; font-size: 1.375rem; font-weight: 700; }
    .ipd__actions { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; }
    :host ::ng-deep .ipd__rev { min-width: 220px; }
    .ipd__desc { color: var(--p-text-muted-color); margin: 0.5rem 0 0; }
    .ipd__form { display: flex; flex-direction: column; gap: 0.375rem; }
    .ipd__form label { font-size: 0.8125rem; font-weight: 600; }
  `],
})
export class InspectionPlanDetailComponent implements AfterViewInit {
  private readonly api            = inject(InspectionPlanApiService);
  private readonly route          = inject(ActivatedRoute);
  private readonly router         = inject(Router);
  private readonly messageService = inject(MessageService);
  private readonly destroyRef     = inject(DestroyRef);
  private readonly cdr            = inject(ChangeDetectorRef);
  private readonly breadcrumbSvc  = inject(BreadcrumbService);

  planId = '';
  plan: InspectionPlanDto | null = null;
  revisionOptions: { label: string; value: number }[] = [];
  selectedRevision: number | null = null;
  viewingRevisionNumber?: number;

  acting = false;
  showReject = false;
  rejectReason = '';

  ngAfterViewInit(): void {
    this.planId = this.route.snapshot.paramMap.get('id') ?? '';
    setTimeout(() => this.reload());
  }

  reload(): void {
    this.api.getPlan(this.planId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: plan => {
        this.plan = plan;
        this.viewingRevisionNumber = plan.revision;
        this.selectedRevision = plan.revision;
        this.breadcrumbSvc.set([
          { label: 'Quality' },
          { label: 'Inspection Plans', route: ['/quality/inspection-plans'] },
          { label: plan.partNumber },
        ]);
        this.loadRevisions();
        this.cdr.detectChanges();
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Failed to load plan' });
        this.cdr.detectChanges();
      },
    });
  }

  private loadRevisions(): void {
    this.api.listRevisions(this.planId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
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
    this.viewingRevisionNumber = this.selectedRevision ?? this.plan?.revision;
    if (this.selectedRevision != null) {
      this.api.getPlan(this.planId, { revisionNumber: this.selectedRevision })
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: plan => { this.plan = plan; this.cdr.detectChanges(); },
        });
    }
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
    this.runAction(this.api.submit(this.planId), 'Submitted for approval');
  }

  approve(): void {
    this.runAction(this.api.approve(this.planId), 'Plan approved');
  }

  createRevision(): void {
    this.runAction(this.api.createRevision(this.planId), 'Draft revision created');
  }

  cancelDraft(): void {
    this.acting = true;
    this.api.cancelDraft(this.planId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.acting = false;
        this.messageService.add({ severity: 'success', summary: 'Draft cancelled' });
        this.cdr.detectChanges();
        this.router.navigate(['/quality/inspection-plans']);
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
    this.api.reject(this.planId, this.rejectReason)
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

  private runAction(obs: import('rxjs').Observable<InspectionPlanDto>, success: string): void {
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
