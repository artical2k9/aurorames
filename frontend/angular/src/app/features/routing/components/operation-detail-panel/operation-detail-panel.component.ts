import {
  ChangeDetectorRef, Component, DestroyRef, EventEmitter, Input, OnChanges, Output, inject,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, of } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { InputTextModule } from 'primeng/inputtext';
import { CheckboxModule } from 'primeng/checkbox';
import { RoutingApiService } from '../../services/routing-api.service';
import { ReferenceDataApiService } from '../../services/reference-data-api.service';
import {
  LabourActivityType, LabourPlanLineDto, MaterialConsumptionDto, OperationDto, QualityVariableDto,
  SignificantProcessTypeDto, SkillRequirementDto, StepFileReferenceDto, SupplierDto, ToolingRequirementDto,
  WorkInstructionLinkDto, WorkCentreDto, LabourCodeDto, LabourPlanTypeDto, OperationResourceDto,
} from '../../models/routing.model';

type Tab = 'Overview' | 'Attributes' | 'Resources' | 'BOM' | 'Documents' | 'Tools' | 'Skills' | 'Variables';

/**
 * Tabbed operation-detail editor for the selected operation (US3 — closes the grid-editor gap).
 * Resources covers labour-plan lines (which is what lets an operation be flagged OSP). All edits
 * are disabled unless {@link editable}. Reference data is resolved to names for display.
 */
@Component({
  selector: 'app-operation-detail-panel',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TableModule, ButtonModule, TagModule, InputTextModule, CheckboxModule,
  ],
  templateUrl: './operation-detail-panel.component.html',
  styleUrls: ['./operation-detail-panel.component.scss'],
})
export class OperationDetailPanelComponent implements OnChanges {
  private readonly api = inject(RoutingApiService);
  private readonly refApi = inject(ReferenceDataApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  @Input({ required: true }) routeId!: string;
  @Input({ required: true }) operation!: OperationDto;
  @Input() editable = false;
  @Output() readonly changed = new EventEmitter<void>();

  readonly tabs: Tab[] = ['Overview', 'Attributes', 'Resources', 'BOM', 'Documents', 'Tools', 'Skills', 'Variables'];
  readonly activityTypes: LabourActivityType[] = ['SETUP', 'RUN', 'INSPECTION', 'TRANSPORT'];
  active: Tab = 'Overview';

  // reference data
  workCentres: WorkCentreDto[] = [];
  labourCodes: LabourCodeDto[] = [];
  labourPlanTypes: LabourPlanTypeDto[] = [];
  significantProcessTypes: SignificantProcessTypeDto[] = [];
  suppliers: SupplierDto[] = [];

  // detail data
  resources: OperationResourceDto[] = [];
  labourPlan: LabourPlanLineDto[] = [];
  materials: MaterialConsumptionDto[] = [];
  tooling: ToolingRequirementDto[] = [];
  skills: SkillRequirementDto[] = [];
  qualityVars: QualityVariableDto[] = [];
  workInstructions: WorkInstructionLinkDto[] = [];
  stepFiles: StepFileReferenceDto[] = [];

  // drafts
  resourceDraft: { workCentreId?: string } = {};
  labourDraft: Partial<LabourPlanLineDto> = { labourActivityType: 'RUN', basis: 'PER_LOT' };
  materialDraft: { bomLineId?: string; mandatory?: boolean } = {};
  toolingDraft: { gageOrToolRef?: string; description?: string } = {};
  skillDraft: { skillId?: string } = {};
  qualityDraft: { inspectionCharacteristicId?: string } = {};
  wiDraft: { workInstructionId?: string } = {};
  stepFileDraft: { reference?: string } = {};
  error = '';

  private loadedFor = '';

  ngOnChanges(): void {
    if (!this.workCentres.length) this.loadReferenceData();
    if (this.operation && this.operation.id !== this.loadedFor) {
      this.loadedFor = this.operation.id;
      this.reload();
    }
  }

  select(tab: Tab): void {
    this.active = tab;
    this.error = '';
  }

  private loadReferenceData(): void {
    forkJoin({
      wc: this.refApi.listWorkCentres().pipe(catchError(() => of([]))),
      lc: this.refApi.listLabourCodes().pipe(catchError(() => of([]))),
      lpt: this.refApi.listLabourPlanTypes().pipe(catchError(() => of([]))),
      sp: this.refApi.listSignificantProcessTypes().pipe(catchError(() => of([]))),
      sup: this.refApi.listSuppliers().pipe(catchError(() => of([]))),
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(r => {
      this.workCentres = r.wc.filter(x => x.active);
      this.labourCodes = r.lc.filter(x => x.active);
      this.labourPlanTypes = r.lpt.filter(x => x.active);
      this.significantProcessTypes = r.sp.filter(x => x.active);
      this.suppliers = r.sup.filter(x => x.active);
      this.cdr.detectChanges();
    });
  }

  reload(): void {
    const r = this.routeId;
    const o = this.operation.id;
    forkJoin({
      resources: this.api.listResources(r, o).pipe(catchError(() => of([]))),
      labourPlan: this.api.listLabourPlan(r, o).pipe(catchError(() => of([]))),
      materials: this.api.listMaterials(r, o).pipe(catchError(() => of([]))),
      tooling: this.api.listTooling(r, o).pipe(catchError(() => of([]))),
      skills: this.api.listSkills(r, o).pipe(catchError(() => of([]))),
      qualityVars: this.api.listQualityVariables(r, o).pipe(catchError(() => of([]))),
      workInstructions: this.api.listWorkInstructions(r, o).pipe(catchError(() => of([]))),
      stepFiles: this.api.listStepFiles(r, o).pipe(catchError(() => of([]))),
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(d => {
      this.resources = d.resources;
      this.labourPlan = d.labourPlan;
      this.materials = d.materials;
      this.tooling = d.tooling;
      this.skills = d.skills;
      this.qualityVars = d.qualityVars;
      this.workInstructions = d.workInstructions;
      this.stepFiles = d.stepFiles;
      this.cdr.detectChanges();
    });
  }

  // ── name resolvers ──────────────────────────────────────────────────────────
  workCentreName(id?: string): string {
    return this.workCentres.find(w => w.id === id)?.name ?? id ?? '—';
  }

  labourPlanTypeName(id?: string): string {
    return this.labourPlanTypes.find(t => t.id === id)?.name ?? '—';
  }

  labourCodeName(id?: string): string {
    return this.labourCodes.find(c => c.id === id)?.name ?? '—';
  }

  significantProcessName(id?: string): string {
    return this.significantProcessTypes.find(s => s.id === id)?.name ?? '—';
  }

  supplierName(id?: string): string {
    return this.suppliers.find(s => s.id === id)?.name ?? '—';
  }

  // ── Attributes (patch operation) ─────────────────────────────────────────────
  patchOp(patch: Partial<OperationDto>): void {
    this.api.patchOperation(this.routeId, this.operation.id, patch)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: op => { this.operation = op; this.changed.emit(); this.cdr.detectChanges(); },
        error: err => { this.error = err?.error?.error ?? 'Update failed'; this.reload(); this.cdr.detectChanges(); },
      });
  }

  // ── generic add/delete helpers ───────────────────────────────────────────────
  private done(): void {
    this.reload();
    this.changed.emit();
    this.cdr.detectChanges();
  }

  private fail(err: { error?: { error?: string } }): void {
    this.error = err?.error?.error ?? 'Action failed';
    this.cdr.detectChanges();
  }

  addResource(): void {
    if (!this.resourceDraft.workCentreId) return;
    this.api.addResource(this.routeId, this.operation.id, { workCentreId: this.resourceDraft.workCentreId })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: () => { this.resourceDraft = {}; this.done(); }, error: e => this.fail(e) });
  }

  deleteResource(id?: string): void {
    if (!id) return;
    this.api.deleteResource(this.routeId, this.operation.id, id)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: () => this.done(), error: e => this.fail(e) });
  }

  addLabour(): void {
    if (this.labourDraft.timeValue == null) return;
    this.api.addLabourPlan(this.routeId, this.operation.id, {
      labourActivityType: this.labourDraft.labourActivityType as LabourActivityType,
      labourPlanTypeId: this.labourDraft.labourPlanTypeId,
      labourCodeId: this.labourDraft.labourCodeId,
      timeValue: this.labourDraft.timeValue as number,
      basis: this.labourDraft.basis ?? 'PER_LOT',
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.labourDraft = { labourActivityType: 'RUN', basis: 'PER_LOT' }; this.done(); }, error: e => this.fail(e),
    });
  }

  deleteLabour(id?: string): void {
    if (!id) return;
    this.api.deleteLabourPlan(this.routeId, this.operation.id, id)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: () => this.done(), error: e => this.fail(e) });
  }

  addMaterial(): void {
    if (!this.materialDraft.bomLineId) return;
    this.api.addMaterial(this.routeId, this.operation.id,
      { bomLineId: this.materialDraft.bomLineId, mandatory: !!this.materialDraft.mandatory })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: () => { this.materialDraft = {}; this.done(); }, error: e => this.fail(e) });
  }

  deleteMaterial(id?: string): void {
    if (!id) return;
    this.api.deleteMaterial(this.routeId, this.operation.id, id)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: () => this.done(), error: e => this.fail(e) });
  }

  addTooling(): void {
    if (!this.toolingDraft.gageOrToolRef) return;
    this.api.addTooling(this.routeId, this.operation.id,
      { gageOrToolRef: this.toolingDraft.gageOrToolRef, description: this.toolingDraft.description })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: () => { this.toolingDraft = {}; this.done(); }, error: e => this.fail(e) });
  }

  deleteTooling(id?: string): void {
    if (!id) return;
    this.api.deleteTooling(this.routeId, this.operation.id, id)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: () => this.done(), error: e => this.fail(e) });
  }

  addSkill(): void {
    if (!this.skillDraft.skillId) return;
    this.api.addSkill(this.routeId, this.operation.id, { skillId: this.skillDraft.skillId })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: () => { this.skillDraft = {}; this.done(); }, error: e => this.fail(e) });
  }

  deleteSkill(id?: string): void {
    if (!id) return;
    this.api.deleteSkill(this.routeId, this.operation.id, id)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: () => this.done(), error: e => this.fail(e) });
  }

  addQuality(): void {
    if (!this.qualityDraft.inspectionCharacteristicId) return;
    this.api.addQualityVariable(this.routeId, this.operation.id,
      { inspectionCharacteristicId: this.qualityDraft.inspectionCharacteristicId })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: () => { this.qualityDraft = {}; this.done(); }, error: e => this.fail(e) });
  }

  deleteQuality(id?: string): void {
    if (!id) return;
    this.api.deleteQualityVariable(this.routeId, this.operation.id, id)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: () => this.done(), error: e => this.fail(e) });
  }

  addWorkInstruction(): void {
    if (!this.wiDraft.workInstructionId) return;
    this.api.addWorkInstruction(this.routeId, this.operation.id,
      { workInstructionId: this.wiDraft.workInstructionId })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: () => { this.wiDraft = {}; this.done(); }, error: e => this.fail(e) });
  }

  deleteWorkInstruction(id?: string): void {
    if (!id) return;
    this.api.deleteWorkInstruction(this.routeId, this.operation.id, id)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: () => this.done(), error: e => this.fail(e) });
  }

  addStepFile(): void {
    if (!this.stepFileDraft.reference) return;
    this.api.addStepFile(this.routeId, this.operation.id, { reference: this.stepFileDraft.reference })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: () => { this.stepFileDraft = {}; this.done(); }, error: e => this.fail(e) });
  }

  deleteStepFile(id?: string): void {
    if (!id) return;
    this.api.deleteStepFile(this.routeId, this.operation.id, id)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: () => this.done(), error: e => this.fail(e) });
  }
}
