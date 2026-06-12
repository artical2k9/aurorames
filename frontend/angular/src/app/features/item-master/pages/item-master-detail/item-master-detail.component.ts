import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { SkeletonModule } from 'primeng/skeleton';
import { TextareaModule } from 'primeng/textarea';
import { SelectModule } from 'primeng/select';
import { LucideFilePenLine, LucideFilePlus } from '@lucide/angular';
import { ItemMasterApiService } from '../../services/item-master-api.service';
import { UdfApiService, UdfFieldDefinition } from '../../../../shared/udf/udf-api.service';
import { ItemMasterDto, ItemRevisionSummaryDto, Classification, RevisionStatus } from '../../models/item-master.model';
import { BreadcrumbService } from '../../../../shared/ui';

@Component({
  selector: 'app-item-master-detail',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    CardModule, ButtonModule, TagModule, SkeletonModule, TextareaModule, SelectModule,
    LucideFilePenLine, LucideFilePlus,
  ],
  template: `

    <div class="imd">

      <!-- Back + actions bar -->
      <div class="imd__topbar">
        <p-button icon="pi pi-arrow-left" label="Back to List" [text]="true"
                  severity="secondary" size="small" (onClick)="goBack()" />
        @if (item && isLatestRevision) {
          <div class="imd__topbar-actions">
            @if (item.revisionStatus === 'DRAFT') {
              <p-button label="Edit Draft" severity="warn" size="small"
                        (onClick)="openEditDraft()">
                <svg lucideFilePenLine [size]="14" [strokeWidth]="1.75" style="margin-right:0.375rem"></svg>
              </p-button>
            }
            @if (item.revisionStatus === 'APPROVED' && !item.hasDraft && !item.hasPendingApproval) {
              <p-button label="Create Revision" severity="primary" size="small"
                        [loading]="creatingRevision" (onClick)="createRevision()">
                <svg lucideFilePlus [size]="14" [strokeWidth]="1.75" style="margin-right:0.375rem"></svg>
              </p-button>
            }
            @if (item.revisionStatus === 'PENDING_APPROVAL') {
              <p-button label="Approve" severity="success" size="small"
                        [loading]="approving" (onClick)="approve()" />
              <p-button label="Reject" severity="danger" size="small"
                        (onClick)="showRejectPanel = true" />
            }
          </div>
        }
      </div>

      @if (showRejectPanel) {
        <div class="imd__reject-panel">
          <textarea pTextarea [(ngModel)]="rejectReason" rows="3" style="width:100%"
                    placeholder="Rejection reason (required)"></textarea>
          <div class="imd__reject-actions">
            <p-button label="Confirm Rejection" severity="danger" size="small"
                      [loading]="rejecting" [disabled]="!rejectReason.trim()"
                      (onClick)="reject()" />
            <p-button label="Cancel" severity="secondary" size="small"
                      (onClick)="showRejectPanel = false; rejectReason = ''" />
          </div>
        </div>
      }

      @if (loading) {
        <!-- Skeleton while loading -->
        <div class="imd__skeleton-grid">
          @for (i of [1,2,3,4,5,6]; track i) {
            <p-skeleton height="2rem" />
          }
        </div>
      } @else if (item) {

        <!-- Header card: part number + status -->
        <p-card styleClass="imd__header-card">
          <div class="imd__header-content">
            <div>
              <div class="imd__pn">{{ item.partNumber }}</div>
              <div class="imd__desc">{{ item.description }}</div>
            </div>
            <div class="imd__header-right">
              @if (revisionOptions.length > 1) {
                <div class="imd__rev-selector">
                  <span class="imd__rev-label">Revision:</span>
                  <p-select [options]="revisionOptions" [(ngModel)]="selectedRevision"
                            optionLabel="label" optionValue="value"
                            styleClass="imd__rev-select"
                            (onChange)="onRevisionChange($event.value)" />
                </div>
              } @else {
                <span class="imd__rev-static">Rev {{ item.revision }}</span>
              }
              <div class="imd__header-badges">
                <p-tag [value]="revisionStatusLabel(item.revisionStatus)"
                       [severity]="revisionStatusSeverity(item.revisionStatus)" />
                <p-tag [value]="item.classification"
                       [severity]="classificationSeverity(item.classification)"
                       [class]="classificationClass(item.classification)" />
              </div>
            </div>
          </div>
        </p-card>

        <!-- Two-column detail grid -->
        <div class="imd__grid">

          <!-- Identity & Classification -->
          <p-card header="Identity & Classification" styleClass="imd__card">
            <div class="imd__fields">
              <div class="imd__row">
                <span class="imd__label">Part Number</span>
                <span class="imd__value">{{ item.partNumber }}</span>
              </div>
              <div class="imd__row">
                <span class="imd__label">Revision</span>
                <span class="imd__value">{{ item.revision }}</span>
              </div>
              <div class="imd__row">
                <span class="imd__label">Description</span>
                <span class="imd__value">{{ item.description }}</span>
              </div>
              <div class="imd__row">
                <span class="imd__label">Classification</span>
                <span class="imd__value">
                  <p-tag [value]="item.classification"
                         [severity]="classificationSeverity(item.classification)"
                         [class]="classificationClass(item.classification)" />
                </span>
              </div>
              <div class="imd__row">
                <span class="imd__label">Make/Buy</span>
                <span class="imd__value">{{ item.makeBuyCode }}</span>
              </div>
              <div class="imd__row">
                <span class="imd__label">Traceability</span>
                <span class="imd__value">{{ item.traceabilityMethod }}</span>
              </div>
              <div class="imd__row">
                <span class="imd__label">Unit of Measure</span>
                <span class="imd__value">{{ item.unitOfMeasure }}</span>
              </div>
              @if (item.cageCode) {
                <div class="imd__row">
                  <span class="imd__label">CAGE Code</span>
                  <span class="imd__value">{{ item.cageCode }}</span>
                </div>
              }
            </div>
          </p-card>

          <!-- Quality & Compliance -->
          <p-card header="Quality & Compliance" styleClass="imd__card">
            <div class="imd__fields">
              <div class="imd__row">
                <span class="imd__label">Shelf Life Controlled</span>
                <span class="imd__value">{{ item.shelfLifeControlled ? 'Yes' : 'No' }}</span>
              </div>
              @if (item.shelfLifeDays) {
                <div class="imd__row">
                  <span class="imd__label">Shelf Life Days</span>
                  <span class="imd__value">{{ item.shelfLifeDays }}</span>
                </div>
              }
              @if (item.counterfeitRiskLevel) {
                <div class="imd__row">
                  <span class="imd__label">Counterfeit Risk</span>
                  <span class="imd__value">
                    <p-tag [value]="item.counterfeitRiskLevel"
                           [severity]="riskSeverity(item.counterfeitRiskLevel)" />
                  </span>
                </div>
              }
              <div class="imd__row">
                <span class="imd__label">Verification Required</span>
                <span class="imd__value">{{ item.verificationRequired ? 'Yes' : 'No' }}</span>
              </div>
              @if (item.approvedSuppliers?.length) {
                <div class="imd__row imd__row--full">
                  <span class="imd__label">Approved Suppliers</span>
                  <span class="imd__value imd__value--pre">{{ item.approvedSuppliers!.join('\n') }}</span>
                </div>
              }
            </div>
          </p-card>

          <!-- Audit -->
          <p-card header="Audit" styleClass="imd__card">
            <div class="imd__fields">
              <div class="imd__row">
                <span class="imd__label">Created By</span>
                <span class="imd__value">{{ item.createdBy }}</span>
              </div>
              <div class="imd__row">
                <span class="imd__label">Created At</span>
                <span class="imd__value">{{ item.createdAt | date:'medium' }}</span>
              </div>
              <div class="imd__row">
                <span class="imd__label">Modified By</span>
                <span class="imd__value">{{ item.modifiedBy }}</span>
              </div>
              <div class="imd__row">
                <span class="imd__label">Modified At</span>
                <span class="imd__value">{{ item.modifiedAt | date:'medium' }}</span>
              </div>
            </div>
          </p-card>

          <!-- Custom Fields (UDF) -->
          @if (udfFields.length > 0) {
            <p-card header="Custom Fields" styleClass="imd__card">
              <div class="imd__fields">
                @for (field of udfFields; track field.fieldKey) {
                  <div class="imd__row">
                    <span class="imd__label">{{ field.label }}</span>
                    <span class="imd__value">
                      {{ udfDisplayValue(field) }}
                    </span>
                  </div>
                }
              </div>
            </p-card>
          }

        </div>

        <!-- Revision History -->
        @if (revisions.length > 1) {
          <p-card header="Revision History" styleClass="imd__card imd__revhistory">
            <table class="imd__revtable">
              <thead>
                <tr>
                  <th>Rev</th>
                  <th>Status</th>
                  <th>Description</th>
                  <th>Approved By</th>
                  <th>Approved At</th>
                  <th>Created By</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                @for (rev of revisionsDesc; track rev.revisionId) {
                  <tr [class.imd__revrow--current]="rev.revision === item.revision">
                    <td>{{ rev.revision }}</td>
                    <td><p-tag [value]="revisionStatusLabel(rev.revisionStatus)"
                               [severity]="revisionStatusSeverity(rev.revisionStatus)" /></td>
                    <td>{{ rev.description ?? '—' }}</td>
                    <td>{{ rev.approvedBy ?? '—' }}</td>
                    <td>{{ rev.approvedAt ? (rev.approvedAt | date:'mediumDate') : '—' }}</td>
                    <td>{{ rev.createdBy }}</td>
                    <td>
                      @if (rev.revision !== item.revision) {
                        <button type="button" class="imd__view-link"
                                (click)="viewRevision(rev.revision)">View</button>
                      } @else {
                        <span class="imd__current-badge">Current</span>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </p-card>
        }

      } @else {
        <div class="imd__not-found">Item not found.</div>
      }

    </div>
  `,
  styles: [`
    .imd { padding: 1.25rem; }

    .imd__topbar {
      display: flex; align-items: center; justify-content: space-between;
      margin-bottom: 1rem;
    }
    .imd__topbar-actions { display: flex; gap: 0.5rem; }

    .imd__skeleton-grid {
      display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; margin-top: 1rem;
    }

    .imd__header-card { margin-bottom: 1rem; }
    .imd__header-content {
      display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem;
    }
    .imd__pn { font-size: 1.375rem; font-weight: 700; }
    .imd__desc { font-size: 0.9375rem; color: var(--p-text-muted-color); margin-top: 0.25rem; }
    .imd__header-right { display: flex; flex-direction: column; align-items: flex-end; gap: 0.5rem; flex-shrink: 0; }
    .imd__header-badges { display: flex; align-items: center; gap: 0.5rem; }
    .imd__rev-selector { display: flex; align-items: center; gap: 0.5rem; }
    .imd__rev-label { font-size: 0.8125rem; color: var(--p-text-muted-color); }
    .imd__rev-static { font-size: 0.9375rem; font-weight: 600; }
    :host ::ng-deep .imd__rev-select { min-width: 120px; font-size: 0.8125rem; }
    .imd__revhistory { margin-top: 1rem; grid-column: 1 / -1; }
    .imd__revtable { width: 100%; border-collapse: collapse; font-size: 0.875rem; }
    .imd__revtable th { text-align: left; padding: 0.4rem 0.75rem; border-bottom: 2px solid var(--p-surface-border); font-weight: 600; color: var(--p-text-muted-color); }
    .imd__revtable td { padding: 0.4rem 0.75rem; border-bottom: 1px solid var(--p-surface-border); }
    .imd__revrow--current td { background: var(--p-surface-50); font-weight: 500; }
    .imd__current-badge { font-size: 0.75rem; color: var(--p-text-muted-color); font-style: italic; }
    .imd__view-link {
      background: none; border: none; padding: 0.25rem 0.5rem; cursor: pointer;
      color: var(--p-primary-color); font-size: 0.8125rem; text-decoration: underline;
    }
    .imd__view-link:hover { color: var(--p-primary-600-color); }

    .imd__grid {
      display: grid; grid-template-columns: 1fr 1fr; gap: 1rem;
    }

    .imd__card { height: 100%; }

    .imd__fields { display: flex; flex-direction: column; gap: 0.625rem; }
    .imd__row {
      display: flex; gap: 0.5rem;
      padding-bottom: 0.5rem;
      border-bottom: 1px solid var(--p-surface-border);
    }
    .imd__row:last-child { border-bottom: none; padding-bottom: 0; }
    .imd__row--full { flex-direction: column; }
    .imd__label {
      min-width: 140px; font-size: 0.8125rem; font-weight: 500;
      color: var(--p-text-muted-color); flex-shrink: 0;
    }
    .imd__value { font-size: 0.9375rem; }
    .imd__value--pre { white-space: pre-line; font-size: 0.875rem; }

    .imd__not-found {
      padding: 2rem; text-align: center; color: var(--p-text-muted-color);
    }

    .imd__reject-panel {
      display: flex; flex-direction: column; gap: 0.5rem;
      background: var(--p-surface-50); border: 1px solid var(--p-red-200);
      border-radius: 6px; padding: 0.875rem; margin-bottom: 0.75rem;
    }
    .imd__reject-actions { display: flex; gap: 0.5rem; }

    :host ::ng-deep .tag-raw-material { background: #0D9488 !important; color: #fff !important; }
    :host ::ng-deep .tag-service { background: #7C3AED !important; color: #fff !important; }
  `],
})
export class ItemMasterDetailComponent implements OnInit {
  private readonly route        = inject(ActivatedRoute);
  private readonly router       = inject(Router);
  private readonly api          = inject(ItemMasterApiService);
  private readonly udfApi       = inject(UdfApiService);
  private readonly breadcrumbSvc = inject(BreadcrumbService);
  private readonly cdr          = inject(ChangeDetectorRef);
  item: ItemMasterDto | null = null;
  udfFields: UdfFieldDefinition[] = [];
  revisions: ItemRevisionSummaryDto[] = [];
  revisionsDesc: ItemRevisionSummaryDto[] = [];
  revisionOptions: { label: string; value: number }[] = [];
  selectedRevision = 0;
  isLatestRevision = true;
  loading = true;
  approving = false;
  rejecting = false;
  creatingRevision = false;
  showRejectPanel = false;
  rejectReason = '';
  itemId!: string;

  ngOnInit(): void {
    this.itemId = this.route.snapshot.paramMap.get('id')!;
    const revParam = this.route.snapshot.queryParamMap.get('revision');
    const targetRevision = revParam != null ? parseInt(revParam, 10) : undefined;
    this.breadcrumbSvc.set([
      { label: 'Master Data' },
      { label: 'Item Master', route: ['/item-master'] },
      { label: 'Detail' },
    ]);
    this.loadRevisions(() => this.loadItem(targetRevision));
    this.udfApi.listFields('ITEM_MASTER').subscribe({
      next: fields => {
        this.udfFields = fields ?? [];
        this.cdr.detectChanges();
      },
      error: () => { this.udfFields = []; },
    });
  }

  private loadRevisions(then: () => void): void {
    this.api.listRevisions(this.itemId).subscribe({
      next: revs => {
        this.revisions = revs;
        this.revisionsDesc = [...revs].sort((a, b) => b.revision - a.revision);
        this.revisionOptions = revs.map(r => ({
          label: 'Rev ' + r.revision + ' (' + this.revisionStatusLabel(r.revisionStatus) + ')',
          value: r.revision,
        }));
        this.cdr.detectChanges();
        then();
      },
      error: () => { then(); },
    });
  }

  private loadItem(revisionNumber?: number): void {
    this.loading = true;
    this.cdr.detectChanges();
    this.api.getById(this.itemId, undefined, revisionNumber).subscribe({
      next: item => {
        this.item = item;
        this.selectedRevision = item.revision;
        const maxRev = this.revisions.length > 0
          ? Math.max(...this.revisions.map(r => r.revision))
          : item.revision;
        this.isLatestRevision = item.revision === maxRev;
        this.breadcrumbSvc.set([
          { label: 'Master Data' },
          { label: 'Item Master', route: ['/item-master'] },
          { label: `${item.partNumber} Rev ${item.revision}` },
        ]);
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => { this.item = null; this.loading = false; this.cdr.detectChanges(); },
    });
  }

  onRevisionChange(revision: number): void {
    if (revision !== this.item?.revision) {
      this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { revision },
        queryParamsHandling: 'merge',
      });
      this.loadItem(revision);
    }
  }

  viewRevision(revision: number): void {
    this.selectedRevision = revision;
    this.loadItem(revision);
  }

  approve(): void {
    this.approving = true;
    this.api.approve(this.itemId).subscribe({
      next: () => {
        this.approving = false;
        this.loadRevisions(() => this.loadItem());
      },
      error: () => { this.approving = false; this.cdr.detectChanges(); },
    });
  }

  reject(): void {
    if (!this.rejectReason.trim()) return;
    this.rejecting = true;
    this.api.reject(this.itemId, this.rejectReason).subscribe({
      next: () => {
        this.rejecting = false;
        this.showRejectPanel = false;
        this.rejectReason = '';
        this.loadRevisions(() => this.loadItem());
      },
      error: () => { this.rejecting = false; this.cdr.detectChanges(); },
    });
  }

  createRevision(): void {
    this.creatingRevision = true;
    this.api.patch(this.itemId, {}).subscribe({
      next: () => {
        this.creatingRevision = false;
        this.router.navigate(['/item-master', this.itemId, 'edit'], {
          queryParams: { revisionStatus: 'DRAFT' },
        });
      },
      error: () => {
        this.creatingRevision = false;
        this.cdr.detectChanges();
        this.router.navigate(['/item-master', this.itemId, 'edit'], {
          queryParams: { revisionStatus: 'DRAFT' },
        });
      },
    });
  }

  openEditDraft(): void {
    this.router.navigate(['/item-master', this.itemId, 'edit'], {
      queryParams: { revisionStatus: 'DRAFT' },
    });
  }

  revisionStatusLabel(s: RevisionStatus): string {
    if (s === 'PENDING_APPROVAL') return 'Pending';
    if (s === 'APPROVED') return 'Approved';
    return 'Draft';
  }

  revisionStatusSeverity(s: RevisionStatus): 'info' | 'warn' | 'success' | 'secondary' {
    if (s === 'APPROVED') return 'success';
    if (s === 'PENDING_APPROVAL') return 'warn';
    return 'secondary';
  }

  goBack(): void {
    this.router.navigate(['/item-master']);
  }

  udfDisplayValue(field: UdfFieldDefinition): string {
    const raw = this.item?.customFields?.[field.fieldKey];
    if (raw === undefined || raw === null) return '—';
    if (field.fieldType === 'BOOLEAN') return raw ? 'Yes' : 'No';
    return String(raw);
  }

  classificationSeverity(c: Classification): 'info' | 'warn' | 'secondary' | 'success' | 'danger' | undefined {
    switch (c) {
      case 'PURCHASED_PART': return 'info';
      case 'FABRICATED':     return 'warn';
      case 'ASSEMBLY':       return 'success';
      case 'COTS':           return 'secondary';
      default:               return undefined;
    }
  }

  classificationClass(c: Classification): string {
    if (c === 'RAW_MATERIAL') return 'tag-raw-material';
    if (c === 'SERVICE')      return 'tag-service';
    return '';
  }

  riskSeverity(level: string): 'info' | 'warn' | 'danger' | 'secondary' {
    switch (level) {
      case 'LOW':      return 'info';
      case 'MEDIUM':   return 'warn';
      case 'HIGH':
      case 'CRITICAL': return 'danger';
      default:         return 'secondary';
    }
  }
}
