import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { FormsModule } from '@angular/forms';
import { BreadcrumbComponent, StatusBadgeComponent } from '../../../../shared/ui';
import { BomApiService } from '../../services/bom-api.service';
import { ItemMasterApiService } from '../../../item-master/services/item-master-api.service';
import { BomDto, CreateBomRequest } from '../../models/bom.model';
import { ItemMasterDto } from '../../../item-master/models/item-master.model';

@Component({
  selector: 'app-bom-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    TableModule, ButtonModule, DialogModule, InputTextModule, MessageModule,
    BreadcrumbComponent, StatusBadgeComponent,
  ],
  template: `
    <div class="bl">
      <app-breadcrumb [crumbs]="breadcrumbs" />

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
            <th>BOM Revision</th>
            <th>Status</th>
            <th>Description</th>
            <th>Created By</th>
            <th>Actions</th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-bom>
          <tr>
            <td>{{ bom.bomRevision }}</td>
            <td><app-status-badge [status]="bom.status" /></td>
            <td>{{ bom.description ?? '—' }}</td>
            <td>{{ bom.createdBy }}</td>
            <td>
              <div class="bl__actions">
                <p-button label="Author" [text]="true" size="small"
                          (onClick)="navigateToAuthoring(bom.id)" />
                <p-button label="Explode" [text]="true" size="small"
                          (onClick)="navigateToExplosion(bom.id)" />
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
          <label>BOM Revision <span class="bl__req">*</span></label>
          <input pInputText [(ngModel)]="newRevision" placeholder="e.g. A" />
        </div>
        <div class="bl__field" style="margin-top:0.75rem">
          <label>Description</label>
          <input pInputText [(ngModel)]="newDescription" placeholder="Optional" />
        </div>
        <ng-template pTemplate="footer">
          <p-button label="Cancel" severity="secondary" size="small"
                    (onClick)="closeCreate()" />
          <p-button label="Save" severity="primary" size="small"
                    [loading]="creating" [disabled]="!newRevision.trim()"
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
    .bl__req { color: #EF4444; }
  `],
})
export class BomListComponent implements OnInit {
  private readonly bomApi = inject(BomApiService);
  private readonly itemApi = inject(ItemMasterApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  itemId = '';
  parentItem: ItemMasterDto | null = null;
  boms: BomDto[] = [];
  loading = false;

  showCreate = false;
  creating = false;
  createError = '';
  newRevision = '';
  newDescription = '';

  readonly breadcrumbs = [
    { label: 'Materials' },
    { label: 'Item Master', route: ['/item-master'] },
    { label: 'BOMs' },
  ];

  ngOnInit(): void {
    this.itemId = this.route.snapshot.paramMap.get('itemId') ?? '';
    this.itemApi.getById(this.itemId).subscribe(item => { this.parentItem = item; });
    this.loadBoms();
  }

  loadBoms(): void {
    this.loading = true;
    this.bomApi.listForItem(this.itemId).subscribe({
      next: boms => { this.boms = boms; this.loading = false; },
      error: () => { this.loading = false; },
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
    this.newRevision = '';
    this.newDescription = '';
    this.createError = '';
  }

  createBom(): void {
    if (!this.newRevision.trim()) return;
    this.creating = true;
    this.createError = '';
    const req: CreateBomRequest = {
      parentItemId: this.itemId,
      bomRevision: this.newRevision.trim(),
      description: this.newDescription.trim() || undefined,
    };
    this.bomApi.create(req).subscribe({
      next: bom => {
        this.creating = false;
        this.closeCreate();
        this.router.navigate(['/boms', bom.id]);
      },
      error: (err: { error?: { message?: string } }) => {
        this.creating = false;
        this.createError = err.error?.message ?? 'Failed to create BOM revision';
      },
    });
  }
}
