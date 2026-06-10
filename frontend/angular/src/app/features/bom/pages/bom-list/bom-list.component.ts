import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { FormsModule } from '@angular/forms';
import { BreadcrumbService } from '../../../../shared/ui';
import { BomApiService } from '../../services/bom-api.service';
import { ItemMasterApiService } from '../../../item-master/services/item-master-api.service';
import { BomSummaryDto, CreateBomRequest, RevisionStatus } from '../../models/bom.model';
import { ItemMasterDto } from '../../../item-master/models/item-master.model';

@Component({
  selector: 'app-bom-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    TableModule, ButtonModule, TagModule, DialogModule, InputTextModule, MessageModule,
  ],
  template: `
    <div class="bl">
      <div class="bl__heading">
        <div>
          <h2 class="bl__title">
            BOMs
            @if (parentItem) {
              &nbsp;<span class="bl__parent">— {{ parentItem.partNumber }} Rev {{ parentItem.revision }}</span>
            }
          </h2>
        </div>
        <p-button label="+ New BOM Revision" severity="primary" size="small"
                  (onClick)="showCreate = true" />
      </div>

      <p-table [value]="boms" [loading]="loading"
               styleClass="p-datatable-gridlines p-datatable-sm">
        <ng-template pTemplate="header">
          <tr>
            <th>BOM Rev</th>
            <th>Status</th>
            <th>Description</th>
            <th>Created By</th>
            <th>Actions</th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-bom>
          <tr>
            <td>{{ bom.revision }}</td>
            <td>
              <p-tag [value]="revisionStatusLabel(bom.revisionStatus)"
                     [severity]="revisionStatusSeverity(bom.revisionStatus)" />
              @if (bom.hasPendingApproval && bom.revisionStatus !== 'PENDING_APPROVAL') {
                <p-tag value="Pending approval" severity="warn" />
              }
            </td>
            <td>{{ bom.description ?? '—' }}</td>
            <td>{{ bom.createdBy }}</td>
            <td>
              <div class="bl__actions">
                <p-button label="Author" [text]="true" size="small"
                          (onClick)="navigateToAuthoring(bom.bomId)" />
                <p-button label="Explode" [text]="true" size="small"
                          (onClick)="navigateToExplosion(bom.bomId)" />
              </div>
            </td>
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage">
          <tr><td colspan="5">No BOM revisions yet.</td></tr>
        </ng-template>
      </p-table>

      <!-- Create dialog -->
      <p-dialog header="New BOM Revision" [(visible)]="showCreate" [modal]="true"
                [style]="{ width: '420px' }">
        @if (createError) {
          <p-message severity="error" [text]="createError" />
        }
        <div class="bl__field">
          <label>Description</label>
          <input pInputText [(ngModel)]="newDescription" placeholder="Optional" />
        </div>
        <ng-template pTemplate="footer">
          <p-button label="Cancel" severity="secondary" size="small"
                    (onClick)="closeCreate()" />
          <p-button label="Save" severity="primary" size="small"
                    [loading]="creating"
                    (onClick)="createBom()" />
        </ng-template>
      </p-dialog>
    </div>
  `,
  styles: [`
    .bl { padding: 1.25rem; }
    .bl__heading {
      display: flex; align-items: baseline; justify-content: space-between;
      margin-bottom: 1rem;
    }
    .bl__title { margin: 0; font-size: 1.375rem; font-weight: 700; }
    .bl__parent { font-weight: 400; font-size: 1rem; color: var(--p-text-muted-color); }
    .bl__actions { display: flex; gap: 0.125rem; }
    .bl__field { display: flex; flex-direction: column; gap: 0.3rem; }
  `],
})
export class BomListComponent implements OnInit {
  private readonly bomApi        = inject(BomApiService);
  private readonly itemApi       = inject(ItemMasterApiService);
  private readonly route         = inject(ActivatedRoute);
  private readonly router        = inject(Router);
  private readonly breadcrumbSvc = inject(BreadcrumbService);
  private readonly destroyRef    = inject(DestroyRef);
  private readonly cdr           = inject(ChangeDetectorRef);

  itemId = '';
  parentItem: ItemMasterDto | null = null;
  boms: BomSummaryDto[] = [];
  loading = false;

  showCreate = false;
  creating = false;
  createError = '';
  newDescription = '';

  ngOnInit(): void {
    this.itemId = this.route.snapshot.paramMap.get('itemId') ?? '';
    this.breadcrumbSvc.set([
      { label: 'Engineering' },
      { label: 'Item Master', route: ['/item-master'] },
      { label: 'BOMs' },
    ]);
    this.itemApi.getById(this.itemId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: item => { this.parentItem = item; this.cdr.detectChanges(); },
      error: () => { /* parent item not found — BOM list still shows without heading */ },
    });
    this.loadBoms();
  }

  loadBoms(): void {
    this.loading = true;
    this.bomApi.listForItem(this.itemId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: boms => { this.boms = boms; this.loading = false; this.cdr.detectChanges(); },
      error: () => { this.loading = false; this.cdr.detectChanges(); },
    });
  }

  navigateToAuthoring(bomId: string): void {
    this.router.navigate(['/boms', bomId]);
  }

  navigateToExplosion(bomId: string): void {
    this.router.navigate(['/boms', bomId, 'explosion']);
  }

  closeCreate(): void {
    this.showCreate = false;
    this.newDescription = '';
    this.createError = '';
  }

  createBom(): void {
    this.creating = true;
    this.createError = '';
    const req: CreateBomRequest = {
      parentItemId: this.itemId,
      description: this.newDescription.trim() || undefined,
    };
    this.bomApi.create(req).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: bom => {
        this.creating = false;
        this.closeCreate();
        this.router.navigate(['/boms', bom.id]);
      },
      error: (err: { error?: { message?: string } }) => {
        this.creating = false;
        this.createError = err.error?.message ?? 'Failed to create BOM revision';
        this.cdr.detectChanges();
      },
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
}
