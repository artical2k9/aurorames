import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { BreadcrumbService } from '../../../../shared/ui';
import { RoutingApiService } from '../../services/routing-api.service';
import { ReferenceDataApiService } from '../../services/reference-data-api.service';
import { OperationGridEditorComponent } from '../../components/operation-grid-editor/operation-grid-editor.component';
import { revisionLabel } from '../../constants/revision-label';
import {
  RouteApprovalStatusDto, RouteDto, RouteStatus, RouteTypeDto, SignificantProcessTypeDto,
} from '../../models/routing.model';

@Component({
  selector: 'app-route-detail',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    ButtonModule, TagModule, DialogModule, InputTextModule, MessageModule, ToastModule,
    OperationGridEditorComponent,
  ],
  providers: [MessageService],
  template: `
    <p-toast />
    @if (route) {
      <div class="rd">
        <div class="rd__header">
          <div>
            <h2 class="rd__title">Route — Part Rev {{ route.partRevision }}</h2>
            <div class="rd__meta">
              <span>{{ routeTypeName(route.routeTypeId) }}</span>
              <span>· Revision {{ revLabel(route.revision) }}</span>
              <p-tag [value]="statusLabel(route.status)" [severity]="statusSeverity(route.status)" />
              @if (route.lockHolder) {
                <span class="rd__lock">🔒 Locked by {{ lockedByMe() ? 'you' : route.lockHolder }}</span>
              } @else {
                <span class="rd__lock rd__lock--open">Unlocked</span>
              }
            </div>
          </div>
          <div class="rd__actions">
            @if (route.status === 'DRAFT' && !route.lockHolder) {
              <p-button label="Lock to edit" size="small" severity="secondary"
                        [disabled]="working" (onClick)="acquireLock()" />
            }
            @if (route.status === 'DRAFT' && lockedByMe()) {
              <p-button label="Save Draft" size="small" severity="secondary"
                        [disabled]="working" (onClick)="saveDraft()" />
              <p-button label="Unlock" size="small" severity="secondary" [text]="true"
                        [disabled]="working" (onClick)="releaseLock()" />
            }
            @if (route.lockHolder && !lockedByMe()) {
              <p-button label="Force unlock" size="small" severity="danger" [text]="true"
                        [disabled]="working" (onClick)="forceUnlock()" />
            }
            @if (route.status === 'DRAFT') {
              <p-button label="Submit for Approval" size="small" severity="primary"
                        [disabled]="working" (onClick)="submit()" />
            }
            @if (route.status === 'PENDING_APPROVAL') {
              <p-button label="Approve" size="small" severity="success" (onClick)="openApprove()" />
              <p-button label="Reject" size="small" severity="danger" (onClick)="openReject()" />
            }
            @if (route.status === 'APPROVED' || route.status === 'REJECTED') {
              <p-button label="Start Revision" size="small" severity="secondary" (onClick)="startRevision()" />
            }
          </div>
        </div>

        @if (route.bomRevisionSuperseded || route.inspectionPlanRevisionSuperseded) {
          <p-message severity="warn"
            text="A newer upstream revision is available (BOM or inspection plan). The pinned revision is unchanged." />
        }

        @if (route.status === 'PENDING_APPROVAL' && approvalStatus) {
          <div class="rd__approval">
            <strong>Approval progress:</strong>
            {{ approvalStatus.standardApprovalPresent ? 'Standard approval recorded' : 'Standard approval outstanding' }}.
            @if (approvalStatus.outstandingSignificantProcessTypeIds.length) {
              <span> Outstanding significant-process approvals:
                {{ outstandingNames(approvalStatus.outstandingSignificantProcessTypeIds) }}.</span>
            } @else {
              <span> No outstanding significant-process approvals.</span>
            }
          </div>
        }

        @if (route.status === 'DRAFT' && !lockedByMe()) {
          <p-message severity="info"
            [text]="route.lockHolder ? 'This route is locked by ' + route.lockHolder + ' — read-only. Force-unlock to take over.' : 'Lock this route to edit it.'" />
        }

        <app-operation-grid-editor [routeId]="route.id"
                                   [editable]="canEdit()"
                                   (changed)="refreshStatus()" />
      </div>

      <!-- Approve dialog -->
      <p-dialog header="Approve Route (e-signature)" [(visible)]="showApprove" [modal]="true"
                [style]="{ width: '440px' }">
        <div class="rd__form">
          @if (approvalStatus && approvalStatus.outstandingSignificantProcessTypeIds.length) {
            <label>Approving as</label>
            <select class="rd__select" [(ngModel)]="approveTypeId">
              <option [ngValue]="undefined">Standard approver</option>
              @for (id of approvalStatus.outstandingSignificantProcessTypeIds; track id) {
                <option [ngValue]="id">SME — {{ significantProcessName(id) }}</option>
              }
            </select>
          }
          <label>Meaning *</label>
          <input pInputText [(ngModel)]="approveMeaning" placeholder="e.g. Approved for production" />
          <label>Password *</label>
          <input pInputText type="password" [(ngModel)]="password" />
          @if (error) { <small class="rd__error">{{ error }}</small> }
        </div>
        <ng-template pTemplate="footer">
          <p-button label="Cancel" severity="secondary" size="small" (onClick)="showApprove = false" />
          <p-button label="Sign & Approve" severity="success" size="small"
                    [disabled]="!password || !approveMeaning || working" (onClick)="approve()" />
        </ng-template>
      </p-dialog>

      <!-- Reject dialog -->
      <p-dialog header="Reject Route (e-signature)" [(visible)]="showReject" [modal]="true"
                [style]="{ width: '440px' }">
        <div class="rd__form">
          <label>Reason</label>
          <input pInputText [(ngModel)]="rejectReason" />
          <label>Password *</label>
          <input pInputText type="password" [(ngModel)]="password" />
          @if (error) { <small class="rd__error">{{ error }}</small> }
        </div>
        <ng-template pTemplate="footer">
          <p-button label="Cancel" severity="secondary" size="small" (onClick)="showReject = false" />
          <p-button label="Sign & Reject" severity="danger" size="small"
                    [disabled]="!password || working" (onClick)="reject()" />
        </ng-template>
      </p-dialog>
    }
  `,
  styles: [`
    .rd { padding: 1.25rem; }
    .rd__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; }
    .rd__title { margin: 0; font-size: 1.375rem; font-weight: 700; }
    .rd__meta { display: flex; align-items: center; gap: 0.5rem; margin-top: 0.25rem;
      font-size: 0.875rem; color: var(--p-text-muted-color); }
    .rd__actions { display: flex; gap: 0.5rem; flex-wrap: wrap; }
    .rd__lock { font-size: 0.8125rem; color: var(--p-orange-500); }
    .rd__lock--open { color: var(--p-text-muted-color); }
    .rd__approval { margin: 0.75rem 0; font-size: 0.875rem; }
    .rd__form { display: flex; flex-direction: column; gap: 0.4rem; }
    .rd__form label { font-size: 0.8125rem; font-weight: 600; margin-top: 0.25rem; }
    .rd__select { padding: 0.4rem 0.5rem; border: 1px solid var(--p-inputtext-border-color); border-radius: 4px; }
    .rd__error { color: var(--p-red-500); }
  `],
})
export class RouteDetailComponent implements OnInit {
  private readonly api = inject(RoutingApiService);
  private readonly refApi = inject(ReferenceDataApiService);
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly messageService = inject(MessageService);
  private readonly breadcrumbSvc = inject(BreadcrumbService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  route: RouteDto | null = null;
  approvalStatus: RouteApprovalStatusDto | null = null;
  routeTypes: RouteTypeDto[] = [];
  significantProcessTypes: SignificantProcessTypeDto[] = [];

  working = false;
  error = '';

  showApprove = false;
  showReject = false;
  password = '';
  approveMeaning = '';
  approveTypeId?: string;
  rejectReason = '';

  ngOnInit(): void {
    this.breadcrumbSvc.set([
      { label: 'Engineering' }, { label: 'Routes', route: ['/routing'] }, { label: 'Detail' },
    ]);
    this.refApi.listRouteTypes().pipe(catchError(() => of([] as RouteTypeDto[])),
      takeUntilDestroyed(this.destroyRef)).subscribe(t => { this.routeTypes = t; this.cdr.detectChanges(); });
    this.refApi.listSignificantProcessTypes().pipe(catchError(() => of([] as SignificantProcessTypeDto[])),
      takeUntilDestroyed(this.destroyRef)).subscribe(s => { this.significantProcessTypes = s; this.cdr.detectChanges(); });
    const id = this.activatedRoute.snapshot.paramMap.get('id');
    if (id) this.load(id);
  }

  private load(id: string): void {
    this.api.get(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: r => { this.route = r; this.refreshStatus(); this.cdr.detectChanges(); },
      error: () => { this.route = null; this.cdr.detectChanges(); },
    });
  }

  refreshStatus(): void {
    if (!this.route) return;
    this.api.approvalStatus(this.route.id).pipe(
      catchError(() => of(null)), takeUntilDestroyed(this.destroyRef),
    ).subscribe(s => { this.approvalStatus = s; this.cdr.detectChanges(); });
  }

  // ── Edit lock (FR-031/032/033) ───────────────────────────────────────────

  /** Current user's KC subject, decoded from the access token (matches RouteLockGuard). */
  private currentSubject(): string | null {
    const token = localStorage.getItem('access_token');
    if (!token) return null;
    try {
      const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
      return (payload.sub as string) ?? (payload.preferred_username as string) ?? null;
    } catch {
      return null;
    }
  }

  lockedByMe(): boolean {
    return !!this.route?.lockHolder && this.route.lockHolder === this.currentSubject();
  }

  canEdit(): boolean {
    return this.route?.status === 'DRAFT' && this.lockedByMe();
  }

  revLabel(revision: number): string {
    return revisionLabel(revision);
  }

  /**
   * Operation/detail edits persist immediately (auto-save on blur), so this re-loads the
   * route to confirm the persisted state and gives the user explicit "saved" feedback while
   * keeping the lock so they can keep editing.
   */
  saveDraft(): void {
    if (!this.route) return;
    this.working = true;
    this.api.get(this.route.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: r => { this.working = false; this.route = r; this.refreshStatus(); this.toast('Draft saved'); this.cdr.detectChanges(); },
      error: err => { this.working = false; this.toastError(err); },
    });
  }

  acquireLock(): void {
    this.lockAction(this.api.acquireLock(this.route!.id), 'Route locked for editing');
  }

  releaseLock(): void {
    this.lockAction(this.api.releaseLock(this.route!.id), 'Route unlocked');
  }

  forceUnlock(): void {
    this.lockAction(this.api.forceUnlock(this.route!.id), 'Lock released');
  }

  private lockAction(call: ReturnType<RoutingApiService['acquireLock']>, ok: string): void {
    this.working = true;
    call.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: r => { this.working = false; this.route = r; this.toast(ok); this.cdr.detectChanges(); },
      error: err => { this.working = false; this.toastError(err); },
    });
  }

  routeTypeName(id: string): string {
    return this.routeTypes.find(t => t.id === id)?.name ?? 'Route';
  }

  significantProcessName(id: string): string {
    return this.significantProcessTypes.find(s => s.id === id)?.name ?? id;
  }

  outstandingNames(ids: string[]): string {
    return ids.map(id => this.significantProcessName(id)).join(', ');
  }

  statusLabel(status: RouteStatus): string {
    switch (status) {
      case 'APPROVED': return 'Approved';
      case 'PENDING_APPROVAL': return 'Pending Approval';
      case 'REJECTED': return 'Rejected';
      default: return 'Draft';
    }
  }

  statusSeverity(status: RouteStatus): 'success' | 'warn' | 'danger' | 'secondary' {
    switch (status) {
      case 'APPROVED': return 'success';
      case 'PENDING_APPROVAL': return 'warn';
      case 'REJECTED': return 'danger';
      default: return 'secondary';
    }
  }

  submit(): void {
    if (!this.route) return;
    this.working = true;
    this.api.submit(this.route.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.working = false; this.reloadCurrent(); this.toast('Submitted for approval'); },
      error: err => { this.working = false; this.toastError(err); },
    });
  }

  openApprove(): void {
    this.password = ''; this.approveMeaning = ''; this.approveTypeId = undefined; this.error = '';
    this.showApprove = true;
  }

  approve(): void {
    if (!this.route) return;
    this.working = true; this.error = '';
    this.api.approve(this.route.id, {
      password: this.password, meaning: this.approveMeaning, significantProcessTypeId: this.approveTypeId,
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: status => {
        this.working = false; this.showApprove = false; this.approvalStatus = status;
        this.reloadCurrent();
        this.toast(status.status === 'APPROVED' ? 'Route approved' : 'Approval recorded');
      },
      error: err => { this.working = false; this.error = err?.error?.error ?? 'Approval failed'; this.cdr.detectChanges(); },
    });
  }

  openReject(): void {
    this.password = ''; this.rejectReason = ''; this.error = '';
    this.showReject = true;
  }

  reject(): void {
    if (!this.route) return;
    this.working = true; this.error = '';
    this.api.reject(this.route.id, { password: this.password, reason: this.rejectReason })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: () => { this.working = false; this.showReject = false; this.reloadCurrent(); this.toast('Route rejected'); },
        error: err => { this.working = false; this.error = err?.error?.error ?? 'Reject failed'; this.cdr.detectChanges(); },
      });
  }

  startRevision(): void {
    if (!this.route) return;
    this.working = true;
    this.api.startRouteRevision(this.route.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: r => { this.working = false; this.route = r; this.refreshStatus(); this.toast('New revision started'); this.cdr.detectChanges(); },
      error: err => { this.working = false; this.toastError(err); },
    });
  }

  private reloadCurrent(): void {
    if (this.route) this.load(this.route.id);
  }

  private toast(summary: string): void {
    this.messageService.add({ severity: 'success', summary });
    this.cdr.detectChanges();
  }

  private toastError(err: { error?: { error?: string } }): void {
    this.messageService.add({ severity: 'error', summary: err?.error?.error ?? 'Action failed' });
    this.cdr.detectChanges();
  }
}
