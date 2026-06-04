import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { SkeletonModule } from 'primeng/skeleton';
import { MessageService, ConfirmationService } from 'primeng/api';
import { BreadcrumbService, StatusBadgeComponent } from '../../../../shared/ui';
import { EcoApiService } from '../../services/eco-api.service';
import { EcoDto } from '../../models/eco.model';

@Component({
  selector: 'app-eco-detail',
  standalone: true,
  imports: [
    CommonModule, RouterLink,
    ButtonModule, CardModule, TagModule, ToastModule, ConfirmDialogModule, SkeletonModule,
    StatusBadgeComponent,
  ],
  providers: [MessageService, ConfirmationService],
  template: `
    <p-toast />
    <p-confirmDialog />

    <div class="ed">
      <div class="ed__topbar">
        <p-button icon="pi pi-arrow-left" label="Back to ECOs" [text]="true"
                  severity="secondary" size="small" (onClick)="goBack()" />
        @if (eco?.status === 'DRAFT') {
          <p-button label="Approve ECO" severity="success" size="small"
                    [loading]="approving" (onClick)="confirmApprove()" />
        }
      </div>

      @if (loading) {
        <div class="ed__skeleton-grid">
          @for (i of [1,2,3,4]; track i) { <p-skeleton height="2rem" /> }
        </div>
      } @else if (eco) {

        <div class="ed__two-col">

          <!-- Left: Change Order Details -->
          <p-card header="Change Order Details" styleClass="ed__card">
            <div class="ed__fields">
              <div class="ed__field">
                <span class="ed__label">ECO Number</span>
                <span class="ed__value">{{ eco.ecoNumber }}</span>
              </div>
              <div class="ed__field">
                <span class="ed__label">Title</span>
                <span class="ed__value">{{ eco.title }}</span>
              </div>
              <div class="ed__field">
                <span class="ed__label">Status</span>
                <app-status-badge [status]="eco.status" />
              </div>
              @if (eco.description) {
                <div class="ed__field">
                  <span class="ed__label">Description</span>
                  <span class="ed__value">{{ eco.description }}</span>
                </div>
              }
              <div class="ed__field">
                <span class="ed__label">Initiated By</span>
                <span class="ed__value">{{ eco.initiatedBy ?? '—' }}</span>
              </div>
              @if (eco.approvedBy) {
                <div class="ed__field">
                  <span class="ed__label">Approved By</span>
                  <span class="ed__value">{{ eco.approvedBy }}</span>
                </div>
              }
              <div class="ed__field">
                <span class="ed__label">Created</span>
                <span class="ed__value">{{ eco.createdAt | date:'medium' }}</span>
              </div>
            </div>
          </p-card>

          <!-- Right: Affected Items + Output BOMs -->
          <div class="ed__right">
            <p-card header="Affected Items" styleClass="ed__card">
              @if (eco.affectedItemIds.length) {
                <div class="ed__chips">
                  @for (itemId of eco.affectedItemIds; track itemId) {
                    <a [routerLink]="['/item-master', itemId]" class="ed__chip">
                      {{ itemId | slice:0:8 }}…
                    </a>
                  }
                </div>
              } @else {
                <span class="ed__empty">No affected items</span>
              }
            </p-card>

            <p-card header="Output BOMs" styleClass="ed__card">
              @if (eco.outputBomIds.length) {
                <div class="ed__chips">
                  @for (bomId of eco.outputBomIds; track bomId) {
                    <a [routerLink]="['/boms', bomId]" class="ed__chip">
                      BOM {{ bomId | slice:0:8 }}…
                    </a>
                  }
                </div>
              } @else {
                <span class="ed__empty">No output BOMs</span>
              }
            </p-card>
          </div>

        </div>
      }
    </div>
  `,
  styles: [`
    .ed { padding: 1.25rem; }
    .ed__topbar {
      display: flex; justify-content: space-between; align-items: center;
      margin-bottom: 1rem;
    }
    .ed__skeleton-grid {
      display: grid; grid-template-columns: repeat(2, 1fr); gap: 0.75rem;
    }
    .ed__two-col { display: grid; grid-template-columns: 1fr 1fr; gap: 1.25rem; }
    .ed__right { display: flex; flex-direction: column; gap: 1rem; }
    :host ::ng-deep .ed__card { height: 100%; }
    .ed__fields { display: flex; flex-direction: column; gap: 0.5rem; }
    .ed__field { display: flex; flex-direction: column; gap: 0.1rem; }
    .ed__label { font-size: 0.75rem; font-weight: 600; color: var(--p-text-muted-color); text-transform: uppercase; }
    .ed__value { font-size: 0.9375rem; }
    .ed__chips { display: flex; flex-wrap: wrap; gap: 0.5rem; }
    .ed__chip {
      display: inline-block; padding: 0.2rem 0.6rem;
      background: var(--p-surface-100); border: 1px solid var(--p-surface-border);
      border-radius: 4px; font-size: 0.8125rem; color: var(--p-primary-color); text-decoration: none;
    }
    .ed__chip:hover { text-decoration: underline; }
    .ed__empty { font-size: 0.8125rem; color: var(--p-text-muted-color); font-style: italic; }
  `],
})
export class EcoDetailComponent implements OnInit {
  private readonly ecoApi = inject(EcoApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly messageService = inject(MessageService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly breadcrumbSvc = inject(BreadcrumbService);

  eco: EcoDto | null = null;
  loading = false;
  approving = false;

  ngOnInit(): void {
    const ecoId = this.route.snapshot.paramMap.get('ecoId') ?? '';
    this.breadcrumbSvc.set([
      { label: 'Home' },
      { label: 'ECOs', route: ['/ecos'] },
      { label: 'Detail' },
    ]);
    this.loading = true;
    this.ecoApi.getById(ecoId).subscribe({
      next: eco => { this.eco = eco; this.loading = false; this.cdr.detectChanges(); },
      error: () => { this.loading = false; this.cdr.detectChanges(); },
    });
  }

  goBack(): void {
    this.router.navigate(['/ecos']);
  }

  confirmApprove(): void {
    this.confirmationService.confirm({
      message: `Approve ECO ${this.eco?.ecoNumber}? This cannot be undone.`,
      accept: () => this.approve(),
    });
  }

  approve(): void {
    this.approving = true;
    this.ecoApi.approve(this.eco!.id).subscribe({
      next: eco => {
        this.eco = eco;
        this.approving = false;
        this.messageService.add({ severity: 'success', summary: 'ECO approved' });
        this.cdr.detectChanges();
      },
      error: () => {
        this.approving = false;
        this.messageService.add({ severity: 'error', summary: 'Approval failed' });
      },
    });
  }
}
