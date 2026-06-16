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
import { RoutingApiService } from '../../services/routing-api.service';
import { ReferenceDataApiService } from '../../services/reference-data-api.service';
import {
  GroupDto, MutuallyExclusiveSetDto, OperationDto, SignificantProcessTypeDto, StepDto, SupplierDto,
  WorkCentreDto,
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
  ],
  template: `
    <div class="oge">
      <div class="oge__bar">
        <h3 class="oge__title">Operations</h3>
        @if (editable) {
          <div class="oge__bar-actions">
            <p-button label="Add Operation" icon="pi pi-plus" size="small"
                      severity="primary" (onClick)="openAddOperation()" />
            <p-button label="Manage Groups" size="small" severity="secondary"
                      (onClick)="openGroups()" />
            <p-button label="Mutually Exclusive" size="small" severity="secondary"
                      (onClick)="openMutuallyExclusive()" />
          </div>
        }
      </div>

      <p-table [value]="operations" dataKey="id" styleClass="p-datatable-sm p-datatable-gridlines">
        <ng-template pTemplate="header">
          <tr>
            <th style="width:4rem">Op #</th>
            <th style="width:4rem">Seq</th>
            <th>Description</th>
            <th style="width:14rem">Type</th>
            <th style="width:12rem">Significant Process</th>
            <th style="width:12rem">OSP Supplier</th>
            <th style="width:9rem">Steps</th>
            @if (editable) { <th style="width:5rem">Actions</th> }
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-op>
          <tr>
            <td>{{ op.operationNumber }}</td>
            <td>{{ op.sequenceNumber }}</td>
            <td>{{ op.description ?? '—' }}</td>
            <td>
              <div class="oge__badges">
                @for (badge of typeBadges(op); track badge) {
                  <p-tag [value]="badge.label" [severity]="badge.severity" />
                }
              </div>
              @if (editable) {
                <div class="oge__toggles">
                  <label><p-checkbox [binary]="true" [ngModel]="op.optional"
                          (ngModelChange)="toggleFlag(op, 'optional', $event)" /> Optional</label>
                  <label><p-checkbox [binary]="true" [ngModel]="op.osp"
                          (ngModelChange)="toggleFlag(op, 'osp', $event)" /> OSP</label>
                </div>
              }
            </td>
            <td>
              @if (editable) {
                <select class="oge__select" [ngModel]="op.significantProcessTypeId ?? ''"
                        (ngModelChange)="setSignificantProcess(op, $event)">
                  <option value="">— None —</option>
                  @for (sp of significantProcessTypes; track sp.id) {
                    <option [value]="sp.id">{{ sp.name }}</option>
                  }
                </select>
              } @else {
                {{ significantProcessName(op.significantProcessTypeId) }}
              }
            </td>
            <td>
              @if (op.osp) {
                @if (editable) {
                  <select class="oge__select" [ngModel]="op.supplierId ?? ''"
                          (ngModelChange)="setSupplier(op, $event)">
                    <option value="">— Unassigned —</option>
                    @for (s of suppliers; track s.id) {
                      <option [value]="s.id">{{ s.name }}</option>
                    }
                  </select>
                } @else {
                  {{ supplierName(op.supplierId) }}
                }
              } @else { <span class="oge__muted">—</span> }
            </td>
            <td>
              <p-button [label]="'Steps (' + (stepCount(op.id)) + ')'" size="small"
                        severity="secondary" [text]="true" (onClick)="openSteps(op)" />
            </td>
            @if (editable) {
              <td>
                <p-button icon="pi pi-trash" size="small" severity="danger" [text]="true"
                          [rounded]="true" title="Delete" (onClick)="deleteOperation(op)" />
              </td>
            }
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage">
          <tr><td [attr.colspan]="editable ? 8 : 7">No operations yet.</td></tr>
        </ng-template>
      </p-table>

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

    <!-- Add operation -->
    <p-dialog header="Add Operation" [(visible)]="showAddOp" [modal]="true" [style]="{ width: '420px' }">
      <div class="oge__form">
        <label>Operation Number *</label>
        <input pInputText type="number" [(ngModel)]="opDraft.operationNumber" />
        <label>Sequence Number *</label>
        <input pInputText type="number" [(ngModel)]="opDraft.sequenceNumber" />
        <small class="oge__hint">Operations sharing a sequence number are derived Parallel.</small>
        <label>Description</label>
        <input pInputText [(ngModel)]="opDraft.description" />
        @if (error) { <small class="oge__error">{{ error }}</small> }
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancel" severity="secondary" size="small" (onClick)="showAddOp = false" />
        <p-button label="Add" severity="primary" size="small"
                  [disabled]="!canAddOp()" (onClick)="addOperation()" />
      </ng-template>
    </p-dialog>

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
  `,
  styles: [`
    .oge { margin-top: 1rem; }
    .oge__bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 0.5rem; }
    .oge__bar-actions { display: flex; gap: 0.5rem; }
    .oge__title { margin: 0; font-size: 1.05rem; font-weight: 700; }
    .oge__subtitle { margin: 1rem 0 0.25rem; font-size: 0.95rem; }
    .oge__badges { display: flex; flex-wrap: wrap; gap: 0.25rem; }
    .oge__toggles { display: flex; gap: 0.75rem; margin-top: 0.35rem; font-size: 0.75rem; }
    .oge__toggles label { display: inline-flex; align-items: center; gap: 0.25rem; }
    .oge__select { width: 100%; padding: 0.3rem 0.4rem; border: 1px solid var(--p-inputtext-border-color); border-radius: 4px; }
    .oge__muted { color: var(--p-text-muted-color); }
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
  @Output() readonly changed = new EventEmitter<void>();

  operations: OperationDto[] = [];
  groups: GroupDto[] = [];
  meSets: MutuallyExclusiveSetDto[] = [];
  significantProcessTypes: SignificantProcessTypeDto[] = [];
  suppliers: SupplierDto[] = [];
  workCentres: WorkCentreDto[] = [];

  private meOperationIds = new Set<string>();
  private stepCounts = new Map<string, number>();

  showAddOp = false;
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
      this.loadStepCounts();
      this.cdr.detectChanges();
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

  typeBadges(op: OperationDto): { label: string; severity: 'info' | 'warn' | 'danger' | 'secondary' }[] {
    const badges: { label: string; severity: 'info' | 'warn' | 'danger' | 'secondary' }[] = [
      op.derivedType === 'PARALLEL'
        ? { label: 'Parallel', severity: 'info' }
        : { label: 'Normal', severity: 'secondary' },
    ];
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

  openAddOperation(): void {
    this.opDraft = {};
    this.error = '';
    this.showAddOp = true;
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
      next: () => { this.showAddOp = false; this.afterChange(); },
      error: err => { this.error = err?.error?.error ?? 'Failed to add operation'; this.cdr.detectChanges(); },
    });
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
