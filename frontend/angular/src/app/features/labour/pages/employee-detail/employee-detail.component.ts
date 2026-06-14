import {
  ChangeDetectorRef, Component, DestroyRef, inject, OnInit,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { SelectModule } from 'primeng/select';
import { DatePickerModule } from 'primeng/datepicker';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { MessageService, ConfirmationService } from 'primeng/api';
import { BreadcrumbService } from '../../../../shared/ui';
import { LabourApiService } from '../../services/labour-api.service';
import {
  AwardCertificationDialogComponent,
} from '../../components/award-certification-dialog/award-certification-dialog.component';
import {
  CertificationDto, CertificationState, EmployeeDto, TrainingHistoryEntryDto,
} from '../../models/labour.model';

@Component({
  selector: 'app-employee-detail',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    ButtonModule, TableModule, TagModule, ToastModule, ConfirmDialogModule,
    DialogModule, InputTextModule, InputNumberModule, SelectModule, DatePickerModule,
    AwardCertificationDialogComponent,
  ],
  providers: [MessageService, ConfirmationService],
  template: `
    <p-toast />
    <p-confirmDialog />

    <div class="emd">
      @if (employee) {
        <div class="emd__heading">
          <div>
            <h2 class="emd__title">{{ employee.firstName }} {{ employee.lastName }}</h2>
            <span class="emd__subtitle">
              {{ employee.employeeNumber }}
              <p-tag [value]="employee.employmentStatus === 'ACTIVE' ? 'Active' : 'Inactive'"
                     [severity]="employee.employmentStatus === 'ACTIVE' ? 'success' : 'secondary'" />
            </span>
          </div>
          <div class="emd__heading-actions">
            <p-button label="Edit Details" severity="secondary" size="small"
                      (onClick)="openEditDetails()" />
            <p-button [label]="employee.employmentStatus === 'ACTIVE' ? 'Deactivate' : 'Activate'"
                      severity="secondary" size="small" (onClick)="toggleStatus()" />
            <p-button label="Award Certification" severity="primary" size="small"
                      (onClick)="showAward = true" />
          </div>
        </div>

        <div class="emd__info">
          <div><span class="emd__label">Email</span>{{ employee.email || '—' }}</div>
          <div><span class="emd__label">Hire Date</span>{{ employee.hireDate || '—' }}</div>
          <div><span class="emd__label">IAM User</span>{{ employee.iamUserId || '—' }}</div>
        </div>

        <h3 class="emd__section">Competency Profile</h3>
        <p-table [value]="certifications" styleClass="p-datatable-gridlines p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>Skill</th>
              <th>Name</th>
              <th>State</th>
              <th>Award Date</th>
              <th>Expiry</th>
              <th>Assessor</th>
              <th style="width:7rem">Actions</th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-cert>
            <tr>
              <td>{{ cert.skillCode }}</td>
              <td>{{ cert.skillName }}</td>
              <td><p-tag [value]="stateLabel(cert.state)" [severity]="stateSeverity(cert.state)" /></td>
              <td>{{ cert.awardDate }}</td>
              <td>{{ cert.expiryDate || 'Never' }}</td>
              <td>{{ cert.assessor || '—' }}</td>
              <td>
                @if (!cert.revoked) {
                  <p-button label="Revoke" [text]="true" size="small" severity="danger"
                            (onClick)="confirmRevoke(cert)" />
                } @else {
                  <p-button label="Reinstate" [text]="true" size="small" severity="success"
                            [loading]="reinstatingSkillId === cert.skillId"
                            (onClick)="reinstate(cert)" />
                }
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="7">No certifications held.</td></tr>
          </ng-template>
        </p-table>

        <div class="emd__section-row">
          <h3 class="emd__section">Training History</h3>
          <p-button label="Add Training Record" icon="pi pi-plus" severity="secondary"
                    size="small" (onClick)="openAddTraining()" />
        </div>
        <p-table [value]="training" styleClass="p-datatable-gridlines p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>Title</th>
              <th>Date</th>
              <th>Duration (min)</th>
              <th>Trainer</th>
              <th>Outcome</th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-record>
            <tr>
              <td>{{ record.title }}</td>
              <td>{{ record.trainingDate }}</td>
              <td>{{ record.durationMinutes ?? '—' }}</td>
              <td>{{ record.trainer || '—' }}</td>
              <td>
                <p-tag [value]="record.outcome"
                       [severity]="record.outcome === 'COMPLETED' ? 'success' : 'danger'" />
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="5">No training records.</td></tr>
          </ng-template>
        </p-table>

        <app-award-certification-dialog
          [visible]="showAward"
          [employeeId]="employee.id"
          (visibleChange)="showAward = $event"
          (awarded)="onAwarded()"
        />

        <p-dialog header="Edit Employee Details" [(visible)]="showEditDetails" [modal]="true"
                  [style]="{ width: '440px' }">
          <div class="emd__form">
            <label>Email</label>
            <input pInputText [(ngModel)]="detailsDraft.email" />
            <label>Hire Date</label>
            <p-datepicker [(ngModel)]="detailsHireDate" dateFormat="yy-mm-dd" [showIcon]="true" />
            <label>IAM User ID</label>
            <input pInputText [(ngModel)]="detailsDraft.iamUserId"
                   placeholder="Keycloak user id" />
            @if (detailsError) {
              <small class="emd__error">{{ detailsError }}</small>
            }
          </div>
          <ng-template pTemplate="footer">
            <p-button label="Cancel" severity="secondary" size="small"
                      (onClick)="showEditDetails = false" />
            <p-button label="Save" severity="primary" size="small"
                      [loading]="savingDetails" (onClick)="saveDetails()" />
          </ng-template>
        </p-dialog>

        <p-dialog header="Add Training Record" [(visible)]="showAddTraining" [modal]="true"
                  [style]="{ width: '460px' }">
          <div class="emd__form">
            <label>Title *</label>
            <input pInputText [(ngModel)]="trainingDraft.title" />
            <label>Training Date *</label>
            <p-datepicker [(ngModel)]="trainingDate" dateFormat="yy-mm-dd" [showIcon]="true" />
            <label>Duration (minutes)</label>
            <p-inputNumber [(ngModel)]="trainingDraft.durationMinutes" [min]="1"
                           [showButtons]="false" />
            <label>Trainer</label>
            <input pInputText [(ngModel)]="trainingDraft.trainer" />
            <label>Outcome *</label>
            <p-select [options]="outcomeOptions" [(ngModel)]="trainingOutcome"
                      optionLabel="label" optionValue="value" />
            <label>Notes</label>
            <input pInputText [(ngModel)]="trainingDraft.notes" />
            @if (trainingError) {
              <small class="emd__error">{{ trainingError }}</small>
            }
          </div>
          <ng-template pTemplate="footer">
            <p-button label="Cancel" severity="secondary" size="small"
                      (onClick)="showAddTraining = false" />
            <p-button label="Save" severity="primary" size="small"
                      [loading]="savingTraining"
                      [disabled]="!trainingDraft.title || !trainingDate"
                      (onClick)="saveTraining()" />
          </ng-template>
        </p-dialog>
      }
    </div>
  `,
  styles: [`
    .emd { padding: 1.25rem; }
    .emd__heading {
      display: flex; align-items: flex-start; justify-content: space-between;
      margin-bottom: 1rem;
    }
    .emd__heading-actions { display: flex; gap: 0.5rem; }
    .emd__title { margin: 0 0 0.25rem; font-size: 1.375rem; font-weight: 700; }
    .emd__subtitle {
      display: inline-flex; align-items: center; gap: 0.5rem;
      font-size: 0.875rem; color: var(--p-text-muted-color);
    }
    .emd__info {
      display: flex; gap: 2rem; margin-bottom: 1.25rem; font-size: 0.875rem;
    }
    .emd__label {
      display: block; font-size: 0.75rem; font-weight: 600;
      color: var(--p-text-muted-color); text-transform: uppercase;
    }
    .emd__section { margin: 1.5rem 0 0.5rem; font-size: 1rem; font-weight: 700; }
    .emd__section-row {
      display: flex; align-items: center; justify-content: space-between;
    }
    .emd__form { display: flex; flex-direction: column; gap: 0.375rem; }
    .emd__form label { font-size: 0.8125rem; font-weight: 600; margin-top: 0.375rem; }
    .emd__error { color: var(--p-red-500); }
  `],
})
export class EmployeeDetailComponent implements OnInit {
  private readonly api = inject(LabourApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly messageService = inject(MessageService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly breadcrumbSvc = inject(BreadcrumbService);

  employee: EmployeeDto | null = null;
  certifications: CertificationDto[] = [];
  training: TrainingHistoryEntryDto[] = [];
  showAward = false;
  reinstatingSkillId = '';

  showEditDetails = false;
  savingDetails = false;
  detailsError = '';
  detailsDraft: Partial<EmployeeDto> = {};
  detailsHireDate: Date | null = null;

  showAddTraining = false;
  savingTraining = false;
  trainingError = '';
  trainingDraft: { title?: string; durationMinutes?: number; trainer?: string; notes?: string } = {};
  trainingDate: Date | null = new Date();
  trainingOutcome = 'COMPLETED';

  readonly outcomeOptions = [
    { label: 'Completed', value: 'COMPLETED' },
    { label: 'Failed', value: 'FAILED' },
  ];

  private employeeId = '';

  ngOnInit(): void {
    this.employeeId = this.route.snapshot.paramMap.get('id') ?? '';
    this.breadcrumbSvc.set([
      { label: 'Labour' },
      { label: 'Employees', route: ['/labour/employees'] },
      { label: 'Detail' },
    ]);
    this.loadAll();
  }

  stateLabel(state: CertificationState): string {
    switch (state) {
      case 'EXPIRING_SOON': return 'Expiring Soon';
      case 'EXPIRED':       return 'Expired';
      case 'REVOKED':       return 'Revoked';
      default:              return 'Active';
    }
  }

  stateSeverity(state: CertificationState): 'success' | 'warn' | 'danger' | 'secondary' {
    switch (state) {
      case 'EXPIRING_SOON': return 'warn';
      case 'EXPIRED':       return 'danger';
      case 'REVOKED':       return 'secondary';
      default:              return 'success';
    }
  }

  toggleStatus(): void {
    if (!this.employee) return;
    const next = this.employee.employmentStatus === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    this.api.patchEmployee(this.employee.id, { employmentStatus: next })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: updated => {
          this.employee = updated;
          this.messageService.add({
            severity: 'success',
            summary: next === 'ACTIVE' ? 'Employee activated' : 'Employee deactivated',
          });
          this.cdr.detectChanges();
        },
        error: () => {
          this.messageService.add({ severity: 'error', summary: 'Update failed' });
          this.cdr.detectChanges();
        },
      });
  }

  confirmRevoke(cert: CertificationDto): void {
    this.confirmationService.confirm({
      header: 'Revoke certification',
      message: `Revoke ${cert.skillCode} for this employee? A reason is recorded as "Revoked via UI".`,
      accept: () => this.revoke(cert),
    });
  }

  onAwarded(): void {
    this.messageService.add({ severity: 'success', summary: 'Certification awarded' });
    this.loadAll();
  }

  openEditDetails(): void {
    if (!this.employee) return;
    this.detailsDraft = {
      email: this.employee.email,
      iamUserId: this.employee.iamUserId,
    };
    this.detailsHireDate = this.employee.hireDate ? new Date(this.employee.hireDate) : null;
    this.detailsError = '';
    this.showEditDetails = true;
  }

  saveDetails(): void {
    if (!this.employee) return;
    this.savingDetails = true;
    this.detailsError = '';
    this.api.patchEmployee(this.employee.id, {
      email: this.detailsDraft.email,
      iamUserId: this.detailsDraft.iamUserId ?? '',
      hireDate: this.detailsHireDate ? toIsoDate(this.detailsHireDate) : undefined,
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: updated => {
        this.employee = updated;
        this.savingDetails = false;
        this.showEditDetails = false;
        this.messageService.add({ severity: 'success', summary: 'Details updated' });
        this.cdr.detectChanges();
      },
      error: err => {
        this.savingDetails = false;
        this.detailsError = err?.error?.error ?? 'Failed to update details';
        this.cdr.detectChanges();
      },
    });
  }

  reinstate(cert: CertificationDto): void {
    if (!this.employee) return;
    this.reinstatingSkillId = cert.skillId;
    this.api.awardCertification({
      employeeId: this.employee.id,
      skillId: cert.skillId,
      awardDate: toIsoDate(new Date()),
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.reinstatingSkillId = '';
        this.messageService.add({ severity: 'success', summary: 'Skill reinstated' });
        this.loadAll();
      },
      error: err => {
        this.reinstatingSkillId = '';
        this.messageService.add({
          severity: 'error',
          summary: err?.error?.error ?? 'Reinstate failed',
        });
        this.cdr.detectChanges();
      },
    });
  }

  openAddTraining(): void {
    this.trainingDraft = {};
    this.trainingDate = new Date();
    this.trainingOutcome = 'COMPLETED';
    this.trainingError = '';
    this.showAddTraining = true;
  }

  saveTraining(): void {
    if (!this.employee || !this.trainingDraft.title || !this.trainingDate) return;
    this.savingTraining = true;
    this.trainingError = '';
    this.api.createTrainingEvent({
      title: this.trainingDraft.title,
      trainingDate: toIsoDate(this.trainingDate),
      durationMinutes: this.trainingDraft.durationMinutes,
      trainer: this.trainingDraft.trainer || undefined,
      notes: this.trainingDraft.notes || undefined,
      attendees: [{ employeeId: this.employee.id, outcome: this.trainingOutcome }],
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.savingTraining = false;
        this.showAddTraining = false;
        this.messageService.add({ severity: 'success', summary: 'Training record added' });
        this.loadAll();
        this.cdr.detectChanges();
      },
      error: err => {
        this.savingTraining = false;
        this.trainingError = err?.error?.error ?? 'Failed to add training record';
        this.cdr.detectChanges();
      },
    });
  }

  private revoke(cert: CertificationDto): void {
    this.api.revokeCertification(cert.id, 'Revoked via UI')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.messageService.add({ severity: 'success', summary: 'Certification revoked' });
          this.loadAll();
        },
        error: () => {
          this.messageService.add({ severity: 'error', summary: 'Revoke failed' });
          this.cdr.detectChanges();
        },
      });
  }

  private loadAll(): void {
    this.api.getEmployeeProfile(this.employeeId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: profile => {
          this.employee = profile.employee;
          this.certifications = profile.certifications;
          this.cdr.detectChanges();
        },
        error: () => {
          this.cdr.detectChanges();
        },
      });
    this.api.getEmployeeTraining(this.employeeId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: page => {
          this.training = page.content;
          this.cdr.detectChanges();
        },
        error: () => {
          this.training = [];
          this.cdr.detectChanges();
        },
      });
  }
}

function toIsoDate(d: Date): string {
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${month}-${day}`;
}
