import {
  ChangeDetectorRef, Component, DestroyRef, EventEmitter, Input, OnInit, Output, inject,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, of } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { TagModule } from 'primeng/tag';
import { InputTextModule } from 'primeng/inputtext';
import { CheckboxModule } from 'primeng/checkbox';
import { LucideCopy, LucideTrash2, LucidePencil } from '@lucide/angular';
import { RoutingApiService } from '../../services/routing-api.service';
import { ReferenceDataApiService } from '../../services/reference-data-api.service';
import { OperationDetailPanelComponent } from '../operation-detail-panel/operation-detail-panel.component';
import {
  ApproveRequest, GroupDto, MutuallyExclusiveSetDto, OperationDto, RouteStatus,
  SignificantProcessTypeDto, StepDto, SupplierDto, WorkCentreDto,
} from '../../models/routing.model';

interface StepDraft { stepNumber?: number; stepSequenceNumber?: number; description?: string; }
interface GroupDraft { name?: string; groupSequenceNumber?: number; operationIds: string[]; }

/**
 * Tabular grid editor for a route's operations, steps, groups and mutually-exclusive sets (US3–US6).
 * Type is shown as a derived Normal/Parallel badge plus Optional/OSP/Mutually-Exclusive badges. All
 * edits are disabled unless the owning route is DRAFT ({@link editable}).
 */
@Component({
  selector: 'app-operation-grid-editor',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    TableModule, ButtonModule, DialogModule, TagModule, InputTextModule, CheckboxModule,
    LucideCopy, LucideTrash2, LucidePencil,
    OperationDetailPanelComponent,
  ],
  template: `
    <div class="oge">
      @if (editable) {
        <div class="oge__bar">
          <p-button label="Manage Groups" size="small" severity="secondary" (onClick)="openGroups()" />
          <p-button label="Mutually Exclusive" size="small" severity="secondary"
                    (onClick)="openMutuallyExclusive()" />
        </div>
      }

      <div class="oge__layout">
        <aside class="oge__sidebar">
          <div class="oge__sb-head">
            <span class="oge__sb-count">Operations: {{ operations.length }}</span>
            @if (editable) {
              <button type="button" class="oge__sb-add" title="Add operation" (click)="startAdd()">+</button>
            }
          </div>
          <input pInputText class="oge__sb-search" placeholder="Search operations…"
                 [(ngModel)]="opSearch" />
          <div class="oge__sb-allhdr">All Operations</div>

          @for (op of filteredOps(); track op.id) {
            <div class="oge__card" [class.oge__card--sel]="selectedOp?.id === op.id" (click)="selectOp(op)">
              <div class="oge__card-seq">{{ op.sequenceNumber }}</div>
              <div class="oge__card-body">
                <div class="oge__card-line">
                  <span class="oge__card-num">Op {{ op.operationNumber }}</span>
                  <span class="oge__type"
                        [class.oge__type--parallel]="op.derivedType === 'PARALLEL'">
                    {{ op.derivedType === 'PARALLEL' ? 'Parallel' : 'Normal' }}
                  </span>
                  @for (b of typeBadges(op); track b.label) {
                    <p-tag [value]="b.label" [severity]="b.severity" />
                  }
                </div>
                <div class="oge__card-desc">{{ op.description || '—' }}</div>
              </div>
              @if (editable) {
                <div class="oge__card-actions">
                  <button type="button" title="Edit operation"
                          (click)="selectOp(op); $event.stopPropagation()">
                    <svg lucidePencil [size]="15" [strokeWidth]="1.75"></svg>
                  </button>
                  <button type="button" title="Duplicate"
                          (click)="duplicate(op); $event.stopPropagation()">
                    <svg lucideCopy [size]="15" [strokeWidth]="1.75"></svg>
                  </button>
                  <button type="button" title="Delete" class="oge__card-del"
                          (click)="deleteOperation(op); $event.stopPropagation()">
                    <svg lucideTrash2 [size]="15" [strokeWidth]="1.75"></svg>
                  </button>
                </div>
              }
            </div>
          }
          @if (!operations.length && !addingOp) { <div class="oge__sb-empty">No operations yet.</div> }

          @if (addingOp) {
            <div class="oge__addrow">
              <input pInputText type="number" placeholder="Op #" [(ngModel)]="opDraft.operationNumber" />
              <input pInputText type="number" placeholder="Seq" [(ngModel)]="opDraft.sequenceNumber" />
              <input pInputText placeholder="Description" [(ngModel)]="opDraft.description" />
              @if (error) { <small class="oge__error">{{ error }}</small> }
              <div class="oge__addrow-btns">
                <p-button label="Add" size="small" [disabled]="!canAddOp()" (onClick)="addOperation()" />
                <p-button label="Cancel" size="small" severity="secondary" [text]="true" (onClick)="cancelAdd()" />
              </div>
            </div>
          }
        </aside>

        <main class="oge__main">
          @if (selectedOp) {
            <div class="oge__main-head">
              <span>Operation {{ selectedOp.operationNumber }} · Seq {{ selectedOp.sequenceNumber }}
                @if (routeApproved) {
                  <p-tag styleClass="oge__rev-tag" [value]="opRevLabel(selectedOp)"
                         [severity]="selectedOp.operationStatus === 'APPROVED' ? 'secondary' : 'warn'" />
                }
              </span>
              <span class="oge__main-actions">
                @if (editable) {
                  <p-button [label]="'Steps (' + stepCount(selectedOp.id) + ')'" size="small"
                            severity="secondary" [text]="true" (onClick)="openSteps(selectedOp)" />
                }
                @if (routeApproved) {
                  @switch (selectedOp.operationStatus) {
                    @case ('APPROVED') {
                      <p-button label="Revise operation" size="small" severity="secondary"
                                [disabled]="working" (onClick)="reviseOp(selectedOp)" />
                    }
                    @case ('DRAFT') {
                      <p-button label="Submit for approval" size="small" severity="primary"
                                [disabled]="working" (onClick)="submitOp(selectedOp)" />
                    }
                    @case ('PENDING_APPROVAL') {
                      <p-button label="Approve (e-sign)" size="small" severity="success"
                                (onClick)="openApproveOp(selectedOp)" />
                    }
                  }
                }
              </span>
            </div>
            @if (opError) { <div class="oge__operr">{{ opError }}</div> }
            <app-operation-detail-panel [routeId]="routeId" [operation]="selectedOp" [editable]="editable"
                                        [contentEditable]="contentEditableFor(selectedOp)"
                                        (changed)="reload()" />
          } @else {
            <div class="oge__placeholder">Select an operation from the list to view and edit its detail.</div>
          }
        </main>
      </div>

      @if (groups.length) {
        <h4 class="oge__subtitle">Groups</h4>
        <ul class="oge__grouplist">
          @for (g of groups; track g.id) {
            <li>
              <strong>{{ g.name || ('Group ' + g.groupSequenceNumber) }}</strong>
              <span class="oge__muted"> — seq {{ g.groupSequenceNumber }},
                {{ g.derivedType === 'PARALLEL' ? 'Parallel' : 'Normal' }}{{ g.optional ? ', Optional' : '' }},
                {{ g.operationIds.length }} op(s)</span>
            </li>
          }
        </ul>
      }
    </div>

    <!-- Steps -->
    <p-dialog [header]="'Steps — Operation ' + (activeOp?.operationNumber ?? '')"
              [(visible)]="showSteps" [modal]="true" [style]="{ width: '560px' }">
      <p-table [value]="activeSteps" styleClass="p-datatable-sm p-datatable-gridlines">
        <ng-template pTemplate="header">
          <tr><th>Step</th><th>Seq</th><th>Description</th><th>Type</th>
            @if (editable) { <th></th> }</tr>
        </ng-template>
        <ng-template pTemplate="body" let-s>
          <tr>
            <td>{{ s.stepNumber }}</td>
            <td>{{ s.stepSequenceNumber }}</td>
            <td>{{ s.description ?? '—' }}</td>
            <td>{{ s.derivedType === 'PARALLEL' ? 'Parallel' : 'Normal' }}{{ s.optional ? ', Optional' : '' }}</td>
            @if (editable) {
              <td><p-button icon="pi pi-trash" size="small" severity="danger" [text]="true"
                            [rounded]="true" (onClick)="deleteStep(s)" /></td>
            }
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage"><tr><td [attr.colspan]="editable ? 5 : 4">No steps.</td></tr></ng-template>
      </p-table>
      @if (editable) {
        <div class="oge__stepform">
          <input pInputText type="number" placeholder="Step #" [(ngModel)]="stepDraft.stepNumber" />
          <input pInputText type="number" placeholder="Seq" [(ngModel)]="stepDraft.stepSequenceNumber" />
          <input pInputText placeholder="Description" [(ngModel)]="stepDraft.description" />
          <p-button label="Add Step" size="small" [disabled]="!canAddStep()" (onClick)="addStep()" />
        </div>
      }
    </p-dialog>

    <!-- Groups -->
    <p-dialog header="Manage Groups" [(visible)]="showGroups" [modal]="true" [style]="{ width: '600px' }">
      <p class="oge__hint">Define groups and assign operations. Groups sharing a sequence number are
        derived Parallel. Saving replaces all groups on this route.</p>
      @for (g of groupDrafts; track $index) {
        <div class="oge__groupdraft">
          <input pInputText placeholder="Name" [(ngModel)]="g.name" />
          <input pInputText type="number" placeholder="Group seq" [(ngModel)]="g.groupSequenceNumber" />
          <div class="oge__opchecks">
            @for (op of operations; track op.id) {
              <label><p-checkbox [binary]="true"
                      [ngModel]="g.operationIds.includes(op.id)"
                      (ngModelChange)="toggleGroupOp(g, op.id, $event)" /> {{ op.operationNumber }}</label>
            }
          </div>
          <p-button icon="pi pi-trash" size="small" severity="danger" [text]="true"
                    (onClick)="removeGroupDraft($index)" />
        </div>
      }
      <p-button label="Add Group Row" size="small" severity="secondary" (onClick)="addGroupDraft()" />
      @if (error) { <small class="oge__error">{{ error }}</small> }
      <ng-template pTemplate="footer">
        <p-button label="Cancel" severity="secondary" size="small" (onClick)="showGroups = false" />
        <p-button label="Save Groups" severity="primary" size="small" (onClick)="saveGroups()" />
      </ng-template>
    </p-dialog>

    <!-- Mutually exclusive -->
    <p-dialog header="Mutually Exclusive Set" [(visible)]="showMe" [modal]="true" [style]="{ width: '520px' }">
      <p class="oge__hint">Mutual exclusivity is only available within a parallel sequence (two or more
        operations sharing a sequence number). Select at least two members.</p>
      <label class="oge__label">Parallel sequence</label>
      <select class="oge__select" [(ngModel)]="meSequence" (ngModelChange)="onMeSequenceChange()">
        <option [ngValue]="undefined" disabled>Select a parallel sequence…</option>
        @for (seq of parallelSequences(); track seq) {
          <option [ngValue]="seq">Sequence {{ seq }}</option>
        }
      </select>
      @if (meSequence !== undefined) {
        <div class="oge__opchecks">
          @for (op of operationsInSequence(meSequence); track op.id) {
            <label><p-checkbox [binary]="true" [ngModel]="meMembers.includes(op.id)"
                    (ngModelChange)="toggleMeMember(op.id, $event)" /> Op {{ op.operationNumber }}</label>
          }
        </div>
      }
      @if (error) { <small class="oge__error">{{ error }}</small> }
      <ng-template pTemplate="footer">
        <p-button label="Cancel" severity="secondary" size="small" (onClick)="showMe = false" />
        <p-button label="Save Set" severity="primary" size="small"
                  [disabled]="meMembers.length < 2" (onClick)="saveMutuallyExclusive()" />
      </ng-template>
    </p-dialog>

    <!-- Operation approval (e-signature) -->
    <p-dialog [header]="'Approve Operation ' + (approveOpTarget?.operationNumber ?? '') + ' (e-signature)'"
              [(visible)]="showApproveOp" [modal]="true" [style]="{ width: '440px' }">
      <div class="oge__form">
        <label>Meaning *</label>
        <input pInputText [(ngModel)]="opMeaning" placeholder="e.g. Operation content approved" />
        <label>Password *</label>
        <input pInputText type="password" [(ngModel)]="opPassword" />
        @if (opError) { <small class="oge__error">{{ opError }}</small> }
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancel" severity="secondary" size="small" (onClick)="showApproveOp = false" />
        <p-button label="Sign & Approve" severity="success" size="small"
                  [disabled]="!opPassword || !opMeaning || working" (onClick)="approveOp()" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .oge { margin-top: 1rem; }
    .oge__bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 0.5rem; }
    .oge__bar-actions { display: flex; gap: 0.5rem; }
    .oge__title { margin: 0; font-size: 1.05rem; font-weight: 700; }
    .oge__subtitle { margin: 1rem 0 0.25rem; font-size: 0.95rem; }
    .oge__detailhdr { margin: 1rem 0 0; font-size: 0.9rem; font-weight: 700; color: var(--p-primary-color); }
    .oge__badges { display: flex; flex-wrap: wrap; gap: 0.25rem; }
    .oge__toggles { display: flex; gap: 0.75rem; margin-top: 0.35rem; font-size: 0.75rem; }
    .oge__toggles label { display: inline-flex; align-items: center; gap: 0.25rem; }
    .oge__select { width: 100%; padding: 0.3rem 0.4rem; border: 1px solid var(--p-inputtext-border-color); border-radius: 4px; }
    .oge__muted { color: var(--p-text-muted-color); }
    .oge__layout { display: flex; gap: 1rem; align-items: flex-start; }
    .oge__sidebar { width: 300px; flex: 0 0 300px; border: 1px solid var(--p-content-border-color); border-radius: 6px; overflow: hidden; }
    .oge__sb-head { display: flex; align-items: center; justify-content: space-between; padding: 0.6rem 0.75rem; font-weight: 700; font-size: 0.9rem; }
    .oge__sb-add { border: 1px solid var(--p-content-border-color); background: transparent; width: 24px; height: 24px; border-radius: 6px; cursor: pointer; color: var(--p-primary-color); font-size: 1.1rem; line-height: 1; }
    .oge__sb-search { width: calc(100% - 1.5rem); margin: 0 0.75rem 0.5rem; }
    .oge__sb-allhdr { padding: 0.35rem 0.75rem; background: var(--p-content-hover-background); font-size: 0.7rem; text-transform: uppercase; letter-spacing: 0.4px; color: var(--p-text-muted-color); }
    .oge__card { display: flex; gap: 0.6rem; padding: 0.6rem 0.75rem; border-top: 1px solid var(--p-content-border-color); cursor: pointer; }
    .oge__card:hover { background: var(--p-content-hover-background); }
    .oge__card--sel { background: color-mix(in srgb, var(--p-primary-color) 12%, transparent); border-left: 3px solid var(--p-primary-color); padding-left: calc(0.75rem - 3px); }
    .oge__card-seq { font-size: 1.1rem; font-weight: 700; min-width: 1.6rem; }
    .oge__card-body { flex: 1; min-width: 0; }
    .oge__card-line { display: flex; align-items: center; gap: 0.3rem; flex-wrap: wrap; }
    .oge__card-num { font-size: 0.72rem; font-weight: 700; color: var(--p-text-muted-color); }
    .oge__card-desc { font-size: 0.8rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .oge__type { font-size: 0.65rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.3px;
      padding: 0.05rem 0.4rem; border-radius: 10px; background: var(--p-content-hover-background);
      color: var(--p-text-color); border: 1px solid var(--p-content-border-color); }
    .oge__type--parallel { background: color-mix(in srgb, var(--p-primary-color) 18%, transparent);
      color: var(--p-primary-color); border-color: transparent; }
    .oge__card-actions { display: flex; gap: 0.25rem; align-items: flex-start; }
    .oge__card-actions button { border: none; background: transparent; cursor: pointer;
      color: var(--p-text-muted-color); display: inline-flex; padding: 0.1rem; line-height: 0; }
    .oge__card-actions button:hover { color: var(--p-primary-color); }
    .oge__card-del:hover { color: var(--p-red-500) !important; }
    .oge__sb-empty { padding: 0.75rem; color: var(--p-text-muted-color); font-size: 0.8rem; }
    .oge__addrow { display: flex; flex-direction: column; gap: 0.4rem; padding: 0.6rem 0.75rem; border-top: 1px solid var(--p-content-border-color); }
    .oge__addrow-btns { display: flex; gap: 0.5rem; }
    .oge__main { flex: 1; min-width: 0; }
    .oge__main-head { display: flex; align-items: center; justify-content: space-between; font-weight: 700; font-size: 0.9rem; gap: 0.5rem; }
    .oge__main-actions { display: inline-flex; align-items: center; gap: 0.4rem; }
    .oge__operr { color: var(--p-red-500); font-size: 0.8125rem; margin: 0.4rem 0; }
    :host ::ng-deep .oge__rev-tag { margin-left: 0.4rem; }
    .oge__placeholder { padding: 2rem; text-align: center; color: var(--p-text-muted-color); border: 1px dashed var(--p-content-border-color); border-radius: 6px; }
    .oge__form, .oge__stepform { display: flex; flex-direction: column; gap: 0.4rem; }
    .oge__stepform { flex-direction: row; align-items: center; margin-top: 0.6rem; flex-wrap: wrap; }
    .oge__form label, .oge__label { font-size: 0.8125rem; font-weight: 600; margin-top: 0.25rem; }
    .oge__hint { font-size: 0.75rem; color: var(--p-text-muted-color); }
    .oge__error { color: var(--p-red-500); }
    .oge__grouplist { margin: 0.25rem 0 0; padding-left: 1.1rem; }
    .oge__groupdraft { display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.5rem; flex-wrap: wrap; }
    .oge__opchecks { display: flex; gap: 0.6rem; flex-wrap: wrap; margin: 0.4rem 0; }
    .oge__opchecks label { display: inline-flex; align-items: center; gap: 0.25rem; font-size: 0.8rem; }
  `],
})
export class OperationGridEditorComponent implements OnInit {
  private readonly api = inject(RoutingApiService);
  private readonly refApi = inject(ReferenceDataApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  @Input({ required: true }) routeId!: string;
  @Input() editable = false;
  /** Owning route status — enables per-operation revision on an APPROVED route (US8). */
  @Input() routeStatus: RouteStatus = 'DRAFT';
  @Output() readonly changed = new EventEmitter<void>();

  // operation revision (on an APPROVED route)
  working = false;
  opError = '';
  showApproveOp = false;
  approveOpTarget: OperationDto | null = null;
  opPassword = '';
  opMeaning = '';

  operations: OperationDto[] = [];
  selectedOp: OperationDto | null = null;
  groups: GroupDto[] = [];
  meSets: MutuallyExclusiveSetDto[] = [];
  significantProcessTypes: SignificantProcessTypeDto[] = [];
  suppliers: SupplierDto[] = [];
  workCentres: WorkCentreDto[] = [];

  private meOperationIds = new Set<string>();
  private stepCounts = new Map<string, number>();

  addingOp = false;
  opSearch = '';
  opDraft: { operationNumber?: number; sequenceNumber?: number; description?: string } = {};
  error = '';

  showSteps = false;
  activeOp: OperationDto | null = null;
  activeSteps: StepDto[] = [];
  stepDraft: StepDraft = {};

  showGroups = false;
  groupDrafts: GroupDraft[] = [];

  showMe = false;
  meSequence?: number;
  meMembers: string[] = [];

  ngOnInit(): void {
    forkJoin({
      sigTypes: this.refApi.listSignificantProcessTypes().pipe(catchError(() => of([]))),
      suppliers: this.refApi.listSuppliers().pipe(catchError(() => of([]))),
      workCentres: this.refApi.listWorkCentres().pipe(catchError(() => of([]))),
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(({ sigTypes, suppliers, workCentres }) => {
      this.significantProcessTypes = sigTypes.filter(s => s.active);
      this.suppliers = suppliers.filter(s => s.active);
      this.workCentres = workCentres.filter(w => w.active);
      this.cdr.detectChanges();
    });
    this.reload();
  }

  reload(): void {
    forkJoin({
      operations: this.api.listOperations(this.routeId).pipe(catchError(() => of([] as OperationDto[]))),
      groups: this.api.listGroups(this.routeId).pipe(catchError(() => of([] as GroupDto[]))),
      meSets: this.api.listMutuallyExclusiveSets(this.routeId)
        .pipe(catchError(() => of([] as MutuallyExclusiveSetDto[]))),
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(({ operations, groups, meSets }) => {
      this.operations = operations;
      this.groups = groups;
      this.meSets = meSets;
      this.meOperationIds = new Set(meSets.filter(s => s.level === 'OPERATION').flatMap(s => s.memberIds));
      if (this.selectedOp) {
        this.selectedOp = operations.find(o => o.id === this.selectedOp!.id) ?? null;
      }
      this.loadStepCounts();
      this.cdr.detectChanges();
    });
  }

  selectOp(op: OperationDto): void {
    this.selectedOp = this.selectedOp?.id === op.id ? null : op;
  }

  // ── Operation revision (US8 — on an APPROVED route) ───────────────────────────
  get routeApproved(): boolean {
    return this.routeStatus === 'APPROVED';
  }

  /** Content-edit mode for the panel: route APPROVED and this operation's revision is open (DRAFT). */
  contentEditableFor(op: OperationDto): boolean {
    return this.routeApproved && op.operationStatus === 'DRAFT';
  }

  opRevLabel(op: OperationDto): string {
    switch (op.operationStatus) {
      case 'DRAFT': return 'Revision draft (rev ' + op.operationRevision + ')';
      case 'PENDING_APPROVAL': return 'Pending approval (rev ' + op.operationRevision + ')';
      default: return 'Rev ' + op.operationRevision;
    }
  }

  reviseOp(op: OperationDto): void {
    this.runOp(this.api.startOperationRevision(this.routeId, op.id));
  }

  submitOp(op: OperationDto): void {
    this.runOp(this.api.submitOperation(this.routeId, op.id));
  }

  openApproveOp(op: OperationDto): void {
    this.approveOpTarget = op;
    this.opPassword = '';
    this.opMeaning = '';
    this.opError = '';
    this.showApproveOp = true;
  }

  approveOp(): void {
    if (!this.approveOpTarget) return;
    const req: ApproveRequest = { password: this.opPassword, meaning: this.opMeaning };
    this.working = true;
    this.opError = '';
    this.api.approveOperation(this.routeId, this.approveOpTarget.id, req)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: () => {
          this.working = false;
          this.showApproveOp = false;
          this.afterChange();
          this.cdr.detectChanges();
        },
        error: err => {
          this.working = false;
          this.opError = err?.error?.error ?? 'Approval failed';
          this.cdr.detectChanges();
        },
      });
  }

  private runOp(call: ReturnType<RoutingApiService['startOperationRevision']>): void {
    this.working = true;
    this.opError = '';
    call.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.working = false; this.afterChange(); this.cdr.detectChanges(); },
      error: err => { this.working = false; this.opError = err?.error?.error ?? 'Action failed'; this.cdr.detectChanges(); },
    });
  }

  private loadStepCounts(): void {
    this.stepCounts = new Map();
    this.operations.forEach(op => {
      this.api.listSteps(this.routeId, op.id).pipe(
        catchError(() => of([] as StepDto[])),
        takeUntilDestroyed(this.destroyRef),
      ).subscribe(steps => {
        this.stepCounts.set(op.id, steps.length);
        this.cdr.detectChanges();
      });
    });
  }

  stepCount(opId: string): number {
    return this.stepCounts.get(opId) ?? 0;
  }

  /** Attribute badges only — the Normal/Parallel type is rendered as its own always-visible chip. */
  typeBadges(op: OperationDto): { label: string; severity: 'info' | 'warn' | 'danger' | 'secondary' }[] {
    const badges: { label: string; severity: 'info' | 'warn' | 'danger' | 'secondary' }[] = [];
    if (op.optional) badges.push({ label: 'Optional', severity: 'warn' });
    if (op.osp) badges.push({ label: 'OSP', severity: 'warn' });
    if (this.meOperationIds.has(op.id)) badges.push({ label: 'Mutually Exclusive', severity: 'danger' });
    return badges;
  }

  significantProcessName(id?: string): string {
    return this.significantProcessTypes.find(s => s.id === id)?.name ?? '—';
  }

  supplierName(id?: string): string {
    return this.suppliers.find(s => s.id === id)?.name ?? '—';
  }

  // ── Operation mutations ──────────────────────────────────────────────────────

  filteredOps(): OperationDto[] {
    const q = this.opSearch.trim().toLowerCase();
    const ops = [...this.operations].sort((a, b) =>
      a.sequenceNumber - b.sequenceNumber || a.operationNumber - b.operationNumber);
    if (!q) return ops;
    return ops.filter(op =>
      String(op.operationNumber).includes(q) || String(op.sequenceNumber).includes(q)
      || (op.description ?? '').toLowerCase().includes(q));
  }

  startAdd(): void {
    const nextSeq = this.operations.length
      ? Math.max(...this.operations.map(o => o.sequenceNumber)) + 10 : 10;
    this.opDraft = { operationNumber: this.nextOpNumber(), sequenceNumber: nextSeq };
    this.error = '';
    this.addingOp = true;
  }

  cancelAdd(): void {
    this.addingOp = false;
    this.opDraft = {};
    this.error = '';
  }

  canAddOp(): boolean {
    return this.opDraft.operationNumber != null && this.opDraft.sequenceNumber != null;
  }

  addOperation(): void {
    this.api.addOperation(this.routeId, {
      operationNumber: this.opDraft.operationNumber as number,
      sequenceNumber: this.opDraft.sequenceNumber as number,
      description: this.opDraft.description,
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.addingOp = false; this.opDraft = {}; this.afterChange(); },
      error: err => { this.error = err?.error?.error ?? 'Failed to add operation'; this.cdr.detectChanges(); },
    });
  }

  /** Client-side duplicate: copies the operation header onto a new operation number. */
  duplicate(op: OperationDto): void {
    this.api.addOperation(this.routeId, {
      operationNumber: this.nextOpNumber(),
      sequenceNumber: op.sequenceNumber,
      description: op.description,
      optional: op.optional,
      significantProcessTypeId: op.significantProcessTypeId,
      labourType: op.labourType,
      clocking: op.clocking,
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => this.afterChange(),
      error: err => { this.error = err?.error?.error ?? 'Failed to duplicate operation'; this.cdr.detectChanges(); },
    });
  }

  private nextOpNumber(): number {
    return this.operations.length
      ? Math.max(...this.operations.map(o => o.operationNumber)) + 10 : 10;
  }

  toggleFlag(op: OperationDto, flag: 'optional' | 'osp', value: boolean): void {
    const patch = flag === 'optional' ? { optional: value } : { osp: value };
    this.api.patchOperation(this.routeId, op.id, patch)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: () => this.afterChange(),
        error: () => this.reload(),
      });
  }

  setSignificantProcess(op: OperationDto, id: string): void {
    this.api.patchOperation(this.routeId, op.id, { significantProcessTypeId: id || undefined })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: () => this.afterChange(), error: () => this.reload() });
  }

  setSupplier(op: OperationDto, id: string): void {
    this.api.patchOperation(this.routeId, op.id, { supplierId: id || undefined })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: () => this.afterChange(), error: () => this.reload() });
  }

  deleteOperation(op: OperationDto): void {
    this.api.deleteOperation(this.routeId, op.id)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: () => this.afterChange(), error: () => this.reload() });
  }

  // ── Steps ────────────────────────────────────────────────────────────────────

  openSteps(op: OperationDto): void {
    this.activeOp = op;
    this.stepDraft = {};
    this.error = '';
    this.api.listSteps(this.routeId, op.id).pipe(
      catchError(() => of([] as StepDto[])), takeUntilDestroyed(this.destroyRef),
    ).subscribe(steps => { this.activeSteps = steps; this.showSteps = true; this.cdr.detectChanges(); });
  }

  canAddStep(): boolean {
    return this.stepDraft.stepNumber != null && this.stepDraft.stepSequenceNumber != null;
  }

  addStep(): void {
    if (!this.activeOp) return;
    this.api.addStep(this.routeId, this.activeOp.id, {
      stepNumber: this.stepDraft.stepNumber as number,
      stepSequenceNumber: this.stepDraft.stepSequenceNumber as number,
      description: this.stepDraft.description,
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.stepDraft = {}; this.refreshSteps(); },
      error: err => { this.error = err?.error?.error ?? 'Failed to add step'; this.cdr.detectChanges(); },
    });
  }

  deleteStep(step: StepDto): void {
    if (!this.activeOp) return;
    this.api.deleteStep(this.routeId, this.activeOp.id, step.id)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.refreshSteps());
  }

  private refreshSteps(): void {
    if (!this.activeOp) return;
    const opId = this.activeOp.id;
    this.api.listSteps(this.routeId, opId).pipe(
      catchError(() => of([] as StepDto[])), takeUntilDestroyed(this.destroyRef),
    ).subscribe(steps => {
      this.activeSteps = steps;
      this.stepCounts.set(opId, steps.length);
      this.cdr.detectChanges();
    });
  }

  // ── Groups ───────────────────────────────────────────────────────────────────

  openGroups(): void {
    this.error = '';
    this.groupDrafts = this.groups.map(g => ({
      name: g.name, groupSequenceNumber: g.groupSequenceNumber, operationIds: [...g.operationIds],
    }));
    if (!this.groupDrafts.length) this.addGroupDraft();
    this.showGroups = true;
  }

  addGroupDraft(): void {
    this.groupDrafts.push({ operationIds: [] });
  }

  removeGroupDraft(index: number): void {
    this.groupDrafts.splice(index, 1);
  }

  toggleGroupOp(group: GroupDraft, opId: string, checked: boolean): void {
    if (checked) {
      if (!group.operationIds.includes(opId)) group.operationIds.push(opId);
    } else {
      group.operationIds = group.operationIds.filter(id => id !== opId);
    }
  }

  saveGroups(): void {
    const payload = this.groupDrafts
      .filter(g => g.groupSequenceNumber != null)
      .map(g => ({
        name: g.name, groupSequenceNumber: g.groupSequenceNumber as number, optional: false,
        operationIds: g.operationIds,
      }));
    this.api.putGroups(this.routeId, payload).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.showGroups = false; this.afterChange(); },
      error: err => { this.error = err?.error?.error ?? 'Failed to save groups'; this.cdr.detectChanges(); },
    });
  }

  // ── Mutually exclusive ─────────────────────────────────────────────────────────

  openMutuallyExclusive(): void {
    this.error = '';
    this.meSequence = undefined;
    this.meMembers = [];
    this.showMe = true;
  }

  parallelSequences(): number[] {
    const counts = new Map<number, number>();
    this.operations.forEach(op => counts.set(op.sequenceNumber, (counts.get(op.sequenceNumber) ?? 0) + 1));
    return [...counts.entries()].filter(([, n]) => n > 1).map(([seq]) => seq).sort((a, b) => a - b);
  }

  operationsInSequence(seq: number): OperationDto[] {
    return this.operations.filter(op => op.sequenceNumber === seq);
  }

  onMeSequenceChange(): void {
    this.meMembers = [];
  }

  toggleMeMember(opId: string, checked: boolean): void {
    if (checked) {
      if (!this.meMembers.includes(opId)) this.meMembers.push(opId);
    } else {
      this.meMembers = this.meMembers.filter(id => id !== opId);
    }
  }

  saveMutuallyExclusive(): void {
    // Preserve existing sets at other levels; replace operation-level sets with the new one.
    const others = this.meSets
      .filter(s => s.level !== 'OPERATION')
      .map(s => ({ level: s.level, memberIds: s.memberIds }));
    const sets = [...others, { level: 'OPERATION' as const, memberIds: this.meMembers }];
    this.api.putMutuallyExclusiveSets(this.routeId, sets)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: () => { this.showMe = false; this.afterChange(); },
        error: err => { this.error = err?.error?.error ?? 'Failed to save set'; this.cdr.detectChanges(); },
      });
  }

  private afterChange(): void {
    this.reload();
    this.changed.emit();
  }
}
