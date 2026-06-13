import {
  AfterViewInit, ChangeDetectorRef, Component, DestroyRef, inject, OnInit,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TableModule } from 'primeng/table';
import { SelectModule } from 'primeng/select';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { BreadcrumbService } from '../../../../shared/ui';
import { LabourApiService } from '../../services/labour-api.service';
import { CertificationDto, CertificationState } from '../../models/labour.model';

@Component({
  selector: 'app-certification-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    TableModule, SelectModule, ButtonModule, TagModule, ToastModule,
  ],
  providers: [MessageService],
  template: `
    <p-toast />

    <div class="cl">
      <div class="cl__heading">
        <div class="cl__heading-left">
          <h2 class="cl__title">Certifications</h2>
          <span class="cl__count">({{ rows.length }} shown)</span>
        </div>
        <p-select [options]="expiryWindowOptions" [(ngModel)]="expiringWithinDays"
                  optionLabel="label" optionValue="value"
                  placeholder="Expiry window" [showClear]="true"
                  (onChange)="reload()" styleClass="cl__window-filter" />
      </div>

      <p-table [value]="rows" [paginator]="true" [rows]="20"
               [loading]="loading"
               styleClass="p-datatable-gridlines p-datatable-sm">
        <ng-template pTemplate="header">
          <tr>
            <th>Employee #</th>
            <th>Skill</th>
            <th>Name</th>
            <th>State</th>
            <th>Award Date</th>
            <th>Expiry</th>
            <th style="width:6rem">Actions</th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-cert>
          <tr>
            <td>{{ cert.employeeNumber }}</td>
            <td>{{ cert.skillCode }}</td>
            <td>{{ cert.skillName }}</td>
            <td><p-tag [value]="stateLabel(cert.state)" [severity]="stateSeverity(cert.state)" /></td>
            <td>{{ cert.awardDate }}</td>
            <td>{{ cert.expiryDate || 'Never' }}</td>
            <td>
              <p-button label="Employee" [text]="true" size="small"
                        (onClick)="navigateToEmployee(cert.employeeId)" />
            </td>
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage">
          <tr><td colspan="7">No certifications found.</td></tr>
        </ng-template>
      </p-table>
    </div>
  `,
  styles: [`
    .cl { padding: 1.25rem; }
    .cl__heading {
      display: flex; align-items: center; justify-content: space-between;
      margin-bottom: 1rem;
    }
    .cl__heading-left { display: flex; align-items: baseline; gap: 0.5rem; }
    .cl__title { margin: 0; font-size: 1.375rem; font-weight: 700; }
    .cl__count { font-size: 0.875rem; color: var(--p-text-muted-color); }
    :host ::ng-deep .cl__window-filter { min-width: 200px; }
  `],
})
export class CertificationListComponent implements OnInit, AfterViewInit {
  private readonly api = inject(LabourApiService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly breadcrumbSvc = inject(BreadcrumbService);

  rows: CertificationDto[] = [];
  loading = true;
  expiringWithinDays: number | null = null;

  readonly expiryWindowOptions = [
    { label: 'Expiring within 30 days', value: 30 },
    { label: 'Expiring within 60 days', value: 60 },
    { label: 'Expiring within 90 days', value: 90 },
  ];

  ngOnInit(): void {
    this.breadcrumbSvc.set([
      { label: 'Labour' },
      { label: 'Certifications' },
    ]);
  }

  ngAfterViewInit(): void {
    setTimeout(() => this.fetchRows());
  }

  reload(): void {
    this.fetchRows();
  }

  navigateToEmployee(employeeId: string): void {
    this.router.navigate(['/labour/employees', employeeId]);
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

  private fetchRows(): void {
    this.loading = true;
    this.api.listCertifications({
      expiringWithinDays: this.expiringWithinDays ?? undefined,
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: page => {
        this.rows = page.content;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.rows = [];
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }
}
