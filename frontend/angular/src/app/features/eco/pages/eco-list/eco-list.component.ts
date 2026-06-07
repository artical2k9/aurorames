import { AfterViewInit, ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { SelectModule } from 'primeng/select';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { MessageService, ConfirmationService } from 'primeng/api';
import { BreadcrumbService, StatusBadgeComponent } from '../../../../shared/ui';
import { EcoApiService } from '../../services/eco-api.service';
import { EcoFormComponent } from '../../components/eco-form/eco-form.component';
import { EcoDto } from '../../models/eco.model';

@Component({
  selector: 'app-eco-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    TableModule, ButtonModule, SelectModule, ToastModule, ConfirmDialogModule,
    StatusBadgeComponent, EcoFormComponent,
  ],
  providers: [MessageService, ConfirmationService],
  template: `
    <p-toast />
    <p-confirmDialog />

    <div class="el">
      <div class="el__heading">
        <h2 class="el__title">Engineering Change Orders</h2>
        <div class="el__heading-actions">
          <p-select [options]="statusOptions" [(ngModel)]="selectedStatus"
                    placeholder="All statuses" [showClear]="true"
                    (onChange)="reload()" styleClass="el__status-filter" />
          <p-button label="+ New ECO" severity="primary" size="small"
                    (onClick)="showCreate = true" />
        </div>
      </div>

      <p-table [value]="rows" [lazy]="true" [lazyLoadOnInit]="false" [paginator]="true"
               [rows]="pageSize" [totalRecords]="totalRecords"
               (onLazyLoad)="onLazyLoad($event)"
               [loading]="loading"
               styleClass="p-datatable-gridlines p-datatable-sm">
        <ng-template pTemplate="header">
          <tr>
            <th>ECO #</th>
            <th>Title</th>
            <th>Status</th>
            <th>Affected Items</th>
            <th>Created By</th>
            <th>Actions</th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-eco>
          <tr>
            <td>{{ eco.ecoNumber }}</td>
            <td>{{ eco.title }}</td>
            <td><app-status-badge [status]="eco.status" /></td>
            <td>{{ eco.affectedItemIds?.length ?? 0 }}</td>
            <td>{{ eco.createdBy }}</td>
            <td>
              <div class="el__actions">
                <p-button label="View" [text]="true" size="small"
                          (onClick)="navigate(eco.id)" />
                @if (eco.status === 'DRAFT') {
                  <p-button label="Approve" [text]="true" size="small" severity="success"
                            (onClick)="confirmApprove(eco)" />
                }
              </div>
            </td>
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage">
          <tr><td colspan="6">No engineering change orders found.</td></tr>
        </ng-template>
      </p-table>

      <!-- Create dialog -->
      <app-eco-form
        [visible]="showCreate"
        (visibleChange)="showCreate = $event"
        (saved)="onEcoCreated($event)"
      />
    </div>
  `,
  styles: [`
    .el { padding: 1.25rem; }
    .el__heading {
      display: flex; align-items: center; justify-content: space-between;
      margin-bottom: 1rem;
    }
    .el__heading-actions { display: flex; gap: 0.5rem; align-items: center; }
    .el__title { margin: 0; font-size: 1.375rem; font-weight: 700; }
    .el__actions { display: flex; gap: 0.125rem; }
    :host ::ng-deep .el__status-filter { min-width: 160px; }
  `],
})
export class EcoListComponent implements OnInit, AfterViewInit {
  private readonly ecoApi = inject(EcoApiService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly messageService = inject(MessageService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly breadcrumbSvc = inject(BreadcrumbService);

  rows: EcoDto[] = [];
  totalRecords = 0;
  loading = true;
  pageSize = 20;
  currentPage = 0;
  showCreate = false;
  selectedStatus: string | null = null;

  readonly statusOptions = [
    { label: 'Draft',       value: 'DRAFT' },
    { label: 'Approved',    value: 'APPROVED' },
    { label: 'Implemented', value: 'IMPLEMENTED' },
  ];

  ngOnInit(): void {
    this.breadcrumbSvc.set([
      { label: 'Engineering' },
      { label: 'Engineering Change Orders' },
    ]);
  }

  ngAfterViewInit(): void {
    setTimeout(() => this.fetchRows());
  }

  onLazyLoad(event: TableLazyLoadEvent): void {
    this.currentPage = (event.first ?? 0) / (event.rows ?? this.pageSize);
    this.pageSize = event.rows ?? this.pageSize;
    this.fetchRows();
  }

  reload(): void {
    this.currentPage = 0;
    this.fetchRows();
  }

  navigate(id: string): void {
    this.router.navigate(['/ecos', id]);
  }

  confirmApprove(eco: EcoDto): void {
    this.confirmationService.confirm({
      message: `Approve ECO ${eco.ecoNumber}? This cannot be undone.`,
      accept: () => this.approve(eco),
    });
  }

  approve(eco: EcoDto): void {
    this.ecoApi.approve(eco.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.messageService.add({ severity: 'success', summary: `ECO ${eco.ecoNumber} approved` });
          this.fetchRows();
        },
        error: () => {
          this.messageService.add({ severity: 'error', summary: 'Approval failed' });
        },
      });
  }

  onEcoCreated(eco: EcoDto): void {
    this.showCreate = false;
    if (eco.concurrentEcoWarning) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Concurrent ECO Warning',
        detail: 'A concurrent ECO exists for one or more affected items — review before approving.',
        life: 8000,
      });
    } else {
      this.messageService.add({ severity: 'success', summary: 'ECO created' });
    }
    this.fetchRows();
  }

  private fetchRows(): void {
    this.loading = true;
    this.ecoApi.list(this.currentPage, this.pageSize)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: page => {
          this.rows = page.content;
          this.totalRecords = page.totalElements;
          this.loading = false;
          this.cdr.detectChanges();
        },
        error: () => {
          this.loading = false;
          this.cdr.detectChanges();
        },
      });
  }
}
