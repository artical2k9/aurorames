import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';
import { CommonModule } from '@angular/common';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { BreadcrumbService } from '../../../../shared/ui';
import { ReferenceDataApiService } from '../../services/reference-data-api.service';
import { IamApiService } from '../../../settings/services/iam-api.service';
import {
  ReferenceDataGridComponent, RefFieldDef, RefRow,
} from '../../components/reference-data-grid/reference-data-grid.component';

/**
 * Routing Settings (US10): reference-data maintenance. Each lookup is a standard editable grid
 * (inline add/edit, activate/deactivate, delete) via {@link ReferenceDataGridComponent}. The
 * significant-process "required approver role" is bound to the IAM role catalogue so it can only be
 * a role that actually exists in user management.
 */
@Component({
  selector: 'app-routing-settings',
  standalone: true,
  imports: [CommonModule, ToastModule, ConfirmDialogModule, ReferenceDataGridComponent],
  providers: [MessageService, ConfirmationService],
  template: `
    <p-toast />
    <p-confirmDialog />
    <div class="rs">
      <h2 class="rs__title">Routing Settings</h2>

      <app-reference-data-grid title="Work Centres / Machines" resource="work-centres"
        [rows]="workCentres" [fields]="codeNameFields" (changed)="reload('work-centres')" />

      <app-reference-data-grid title="Labour Codes" resource="labour-codes"
        [rows]="labourCodes" [fields]="codeNameFields" (changed)="reload('labour-codes')" />

      <app-reference-data-grid title="Labour Plan Types" resource="labour-plan-types"
        [rows]="labourPlanTypes" [fields]="codeNameFields" (changed)="reload('labour-plan-types')" />

      <app-reference-data-grid title="Suppliers" resource="suppliers"
        [rows]="suppliers" [fields]="codeNameFields" (changed)="reload('suppliers')" />

      <app-reference-data-grid title="Route Types" resource="route-types"
        [rows]="routeTypes" [fields]="routeTypeFields"
        hint="Only one Standard route type may exist; the server rejects a second."
        (changed)="reload('route-types')" />

      <app-reference-data-grid title="Significant Process Types" resource="significant-process-types"
        [rows]="significantProcessTypes" [fields]="sigFields"
        hint="Required approver role is chosen from the roles defined in user management."
        (changed)="reload('significant-process-types')" />
    </div>
  `,
  styles: [`
    .rs { padding: 1.25rem; }
    .rs__title { margin: 0 0 1rem; font-size: 1.375rem; font-weight: 700; }
  `],
})
export class RoutingSettingsComponent implements OnInit {
  private readonly refApi = inject(ReferenceDataApiService);
  private readonly iamApi = inject(IamApiService);
  private readonly breadcrumbSvc = inject(BreadcrumbService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  workCentres: RefRow[] = [];
  labourCodes: RefRow[] = [];
  labourPlanTypes: RefRow[] = [];
  suppliers: RefRow[] = [];
  routeTypes: RefRow[] = [];
  significantProcessTypes: RefRow[] = [];

  readonly codeNameFields: RefFieldDef[] = [
    { key: 'code', label: 'Code', type: 'text', required: true, width: '12rem' },
    { key: 'name', label: 'Name', type: 'text', required: true },
  ];

  readonly routeTypeFields: RefFieldDef[] = [
    { key: 'code', label: 'Code', type: 'text', required: true, width: '10rem' },
    { key: 'name', label: 'Name', type: 'text', required: true },
    { key: 'isStandard', label: 'Standard', type: 'checkbox', width: '7rem' },
  ];

  /** Built once roles load, so the approver role is constrained to real IAM roles. */
  sigFields: RefFieldDef[] = [
    { key: 'code', label: 'Code', type: 'text', required: true, width: '10rem' },
    { key: 'name', label: 'Name', type: 'text', required: true },
    { key: 'requiredApproverRole', label: 'Required Approver Role', type: 'select', required: true, options: [] },
  ];

  ngOnInit(): void {
    this.breadcrumbSvc.set([{ label: 'Settings' }, { label: 'Routing Settings' }]);
    this.iamApi.listRoles().pipe(catchError(() => of([])), takeUntilDestroyed(this.destroyRef))
      .subscribe(roles => {
        const options = roles.map(r => ({ label: r.name, value: r.name }));
        this.sigFields = this.sigFields.map(f =>
          f.key === 'requiredApproverRole' ? { ...f, options } : f);
        this.cdr.detectChanges();
      });
    this.reloadAll();
  }

  private reloadAll(): void {
    (['work-centres', 'labour-codes', 'labour-plan-types', 'suppliers', 'route-types',
      'significant-process-types'] as const).forEach(r => this.reload(r));
  }

  reload(resource: string): void {
    const loaders: Record<string, () => void> = {
      'work-centres': () => this.refApi.listWorkCentres().pipe(catchError(() => of([])),
        takeUntilDestroyed(this.destroyRef)).subscribe(r => { this.workCentres = r; this.cdr.detectChanges(); }),
      'labour-codes': () => this.refApi.listLabourCodes().pipe(catchError(() => of([])),
        takeUntilDestroyed(this.destroyRef)).subscribe(r => { this.labourCodes = r; this.cdr.detectChanges(); }),
      'labour-plan-types': () => this.refApi.listLabourPlanTypes().pipe(catchError(() => of([])),
        takeUntilDestroyed(this.destroyRef)).subscribe(r => { this.labourPlanTypes = r; this.cdr.detectChanges(); }),
      suppliers: () => this.refApi.listSuppliers().pipe(catchError(() => of([])),
        takeUntilDestroyed(this.destroyRef)).subscribe(r => { this.suppliers = r; this.cdr.detectChanges(); }),
      'route-types': () => this.refApi.listRouteTypes().pipe(catchError(() => of([])),
        takeUntilDestroyed(this.destroyRef)).subscribe(r => { this.routeTypes = r; this.cdr.detectChanges(); }),
      'significant-process-types': () => this.refApi.listSignificantProcessTypes().pipe(catchError(() => of([])),
        takeUntilDestroyed(this.destroyRef)).subscribe(r => { this.significantProcessTypes = r; this.cdr.detectChanges(); }),
    };
    loaders[resource]?.();
  }
}
