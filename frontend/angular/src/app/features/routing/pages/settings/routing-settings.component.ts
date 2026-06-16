import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { BreadcrumbService } from '../../../../shared/ui';
import { ReferenceDataApiService } from '../../services/reference-data-api.service';
import {
  LabourCodeDto, LabourPlanTypeDto, RouteTypeDto, SignificantProcessTypeDto, SupplierDto, WorkCentreDto,
} from '../../models/routing.model';

@Component({
  selector: 'app-routing-settings',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TableModule, ButtonModule, InputTextModule, TagModule, ToastModule,
  ],
  providers: [MessageService],
  template: `
    <p-toast />
    <div class="rs">
      <h2 class="rs__title">Routing Settings</h2>

      <!-- Simple code/name reference lists -->
      @for (section of simpleSections; track section.key) {
        <section class="rs__section">
          <h3>{{ section.label }}</h3>
          <p-table [value]="section.rows()" styleClass="p-datatable-sm p-datatable-gridlines">
            <ng-template pTemplate="header"><tr><th>Code</th><th>Name</th><th>Active</th></tr></ng-template>
            <ng-template pTemplate="body" let-row>
              <tr>
                <td>{{ row.code }}</td>
                <td>{{ row.name }}</td>
                <td><p-tag [value]="row.active ? 'Active' : 'Inactive'"
                           [severity]="row.active ? 'success' : 'secondary'" /></td>
              </tr>
            </ng-template>
            <ng-template pTemplate="emptymessage"><tr><td colspan="3">None defined.</td></tr></ng-template>
          </p-table>
          <div class="rs__form">
            <input pInputText placeholder="Code" [(ngModel)]="section.draft.code" />
            <input pInputText placeholder="Name" [(ngModel)]="section.draft.name" />
            <p-button label="Add" size="small"
                      [disabled]="!section.draft.code || !section.draft.name"
                      (onClick)="section.add()" />
          </div>
        </section>
      }

      <!-- Route types (with isStandard) -->
      <section class="rs__section">
        <h3>Route Types</h3>
        <p-table [value]="routeTypes" styleClass="p-datatable-sm p-datatable-gridlines">
          <ng-template pTemplate="header"><tr><th>Code</th><th>Name</th><th>Standard</th><th>Active</th></tr></ng-template>
          <ng-template pTemplate="body" let-row>
            <tr>
              <td>{{ row.code }}</td>
              <td>{{ row.name }}</td>
              <td>{{ row.isStandard ? 'Yes' : 'No' }}</td>
              <td><p-tag [value]="row.active ? 'Active' : 'Inactive'"
                         [severity]="row.active ? 'success' : 'secondary'" /></td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage"><tr><td colspan="4">None defined.</td></tr></ng-template>
        </p-table>
        <div class="rs__form">
          <input pInputText placeholder="Code" [(ngModel)]="routeTypeDraft.code" />
          <input pInputText placeholder="Name" [(ngModel)]="routeTypeDraft.name" />
          <label class="rs__check"><input type="checkbox" [(ngModel)]="routeTypeDraft.isStandard" /> Standard</label>
          <p-button label="Add" size="small"
                    [disabled]="!routeTypeDraft.code || !routeTypeDraft.name" (onClick)="addRouteType()" />
        </div>
        <small class="rs__hint">Only one Standard route type may exist; the server rejects a second.</small>
      </section>

      <!-- Significant process types (with required approver role) -->
      <section class="rs__section">
        <h3>Significant Process Types</h3>
        <p-table [value]="significantProcessTypes" styleClass="p-datatable-sm p-datatable-gridlines">
          <ng-template pTemplate="header"><tr><th>Code</th><th>Name</th><th>Approver Role</th><th>Active</th></tr></ng-template>
          <ng-template pTemplate="body" let-row>
            <tr>
              <td>{{ row.code }}</td>
              <td>{{ row.name }}</td>
              <td>{{ row.requiredApproverRole }}</td>
              <td><p-tag [value]="row.active ? 'Active' : 'Inactive'"
                         [severity]="row.active ? 'success' : 'secondary'" /></td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage"><tr><td colspan="4">None defined.</td></tr></ng-template>
        </p-table>
        <div class="rs__form">
          <input pInputText placeholder="Code" [(ngModel)]="sigDraft.code" />
          <input pInputText placeholder="Name" [(ngModel)]="sigDraft.name" />
          <input pInputText placeholder="Required approver role" [(ngModel)]="sigDraft.requiredApproverRole" />
          <p-button label="Add" size="small"
                    [disabled]="!sigDraft.code || !sigDraft.name || !sigDraft.requiredApproverRole"
                    (onClick)="addSignificantProcessType()" />
        </div>
      </section>
    </div>
  `,
  styles: [`
    .rs { padding: 1.25rem; }
    .rs__title { margin: 0 0 1rem; font-size: 1.375rem; font-weight: 700; }
    .rs__section { margin-bottom: 1.75rem; }
    .rs__section h3 { font-size: 1.05rem; margin: 0 0 0.5rem; }
    .rs__form { display: flex; align-items: center; gap: 0.5rem; margin-top: 0.5rem; flex-wrap: wrap; }
    .rs__check { display: inline-flex; align-items: center; gap: 0.3rem; font-size: 0.85rem; }
    .rs__hint { display: block; margin-top: 0.35rem; font-size: 0.75rem; color: var(--p-text-muted-color); }
  `],
})
export class RoutingSettingsComponent implements OnInit {
  private readonly refApi = inject(ReferenceDataApiService);
  private readonly messageService = inject(MessageService);
  private readonly breadcrumbSvc = inject(BreadcrumbService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  workCentres: WorkCentreDto[] = [];
  labourCodes: LabourCodeDto[] = [];
  labourPlanTypes: LabourPlanTypeDto[] = [];
  suppliers: SupplierDto[] = [];
  routeTypes: RouteTypeDto[] = [];
  significantProcessTypes: SignificantProcessTypeDto[] = [];

  routeTypeDraft: { code: string; name: string; isStandard: boolean } = { code: '', name: '', isStandard: false };
  sigDraft: { code: string; name: string; requiredApproverRole: string } =
    { code: '', name: '', requiredApproverRole: '' };

  readonly simpleSections = [
    {
      key: 'work-centres', label: 'Work Centres / Machines',
      rows: () => this.workCentres, draft: { code: '', name: '' } as { code: string; name: string },
      add: () => this.addSimple('work-centres'),
    },
    {
      key: 'labour-codes', label: 'Labour Codes',
      rows: () => this.labourCodes, draft: { code: '', name: '' } as { code: string; name: string },
      add: () => this.addSimple('labour-codes'),
    },
    {
      key: 'labour-plan-types', label: 'Labour Plan Types',
      rows: () => this.labourPlanTypes, draft: { code: '', name: '' } as { code: string; name: string },
      add: () => this.addSimple('labour-plan-types'),
    },
    {
      key: 'suppliers', label: 'Suppliers',
      rows: () => this.suppliers, draft: { code: '', name: '' } as { code: string; name: string },
      add: () => this.addSimple('suppliers'),
    },
  ];

  ngOnInit(): void {
    this.breadcrumbSvc.set([{ label: 'Engineering' }, { label: 'Routing Settings' }]);
    this.reloadAll();
  }

  private reloadAll(): void {
    this.refApi.listWorkCentres().pipe(catchError(() => of([])), takeUntilDestroyed(this.destroyRef))
      .subscribe(r => { this.workCentres = r; this.cdr.detectChanges(); });
    this.refApi.listLabourCodes().pipe(catchError(() => of([])), takeUntilDestroyed(this.destroyRef))
      .subscribe(r => { this.labourCodes = r; this.cdr.detectChanges(); });
    this.refApi.listLabourPlanTypes().pipe(catchError(() => of([])), takeUntilDestroyed(this.destroyRef))
      .subscribe(r => { this.labourPlanTypes = r; this.cdr.detectChanges(); });
    this.refApi.listSuppliers().pipe(catchError(() => of([])), takeUntilDestroyed(this.destroyRef))
      .subscribe(r => { this.suppliers = r; this.cdr.detectChanges(); });
    this.refApi.listRouteTypes().pipe(catchError(() => of([])), takeUntilDestroyed(this.destroyRef))
      .subscribe(r => { this.routeTypes = r; this.cdr.detectChanges(); });
    this.refApi.listSignificantProcessTypes().pipe(catchError(() => of([])), takeUntilDestroyed(this.destroyRef))
      .subscribe(r => { this.significantProcessTypes = r; this.cdr.detectChanges(); });
  }

  private addSimple(key: string): void {
    const section = this.simpleSections.find(s => s.key === key);
    if (!section) return;
    const body = { code: section.draft.code, name: section.draft.name, active: true };
    const call = {
      'work-centres': () => this.refApi.createWorkCentre(body),
      'labour-codes': () => this.refApi.createLabourCode(body),
      'labour-plan-types': () => this.refApi.createLabourPlanType(body),
      suppliers: () => this.refApi.createSupplier(body),
    }[key];
    if (!call) return;
    call().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { section.draft.code = ''; section.draft.name = ''; this.reloadAll(); this.ok(); },
      error: err => this.fail(err),
    });
  }

  addRouteType(): void {
    this.refApi.createRouteType({
      code: this.routeTypeDraft.code, name: this.routeTypeDraft.name,
      isStandard: this.routeTypeDraft.isStandard, active: true,
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.routeTypeDraft = { code: '', name: '', isStandard: false }; this.reloadAll(); this.ok(); },
      error: err => this.fail(err),
    });
  }

  addSignificantProcessType(): void {
    this.refApi.createSignificantProcessType({
      code: this.sigDraft.code, name: this.sigDraft.name,
      requiredApproverRole: this.sigDraft.requiredApproverRole, active: true,
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.sigDraft = { code: '', name: '', requiredApproverRole: '' }; this.reloadAll(); this.ok(); },
      error: err => this.fail(err),
    });
  }

  private ok(): void {
    this.messageService.add({ severity: 'success', summary: 'Saved' });
    this.cdr.detectChanges();
  }

  private fail(err: { error?: { error?: string } }): void {
    this.messageService.add({ severity: 'error', summary: err?.error?.error ?? 'Save failed' });
    this.cdr.detectChanges();
  }
}
