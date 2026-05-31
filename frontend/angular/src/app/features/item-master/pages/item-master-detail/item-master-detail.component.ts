import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { SkeletonModule } from 'primeng/skeleton';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { ItemMasterApiService } from '../../services/item-master-api.service';
import { UdfApiService, UdfFieldDefinition } from '../../services/udf-api.service';
import { ItemMasterDto, Classification } from '../../models/item-master.model';
import { StatusBadgeComponent } from '../../../../shared/ui';
import { ItemMasterFormComponent } from '../../components/item-master-form/item-master-form.component';

@Component({
  selector: 'app-item-master-detail',
  standalone: true,
  imports: [
    CommonModule,
    CardModule, ButtonModule, TagModule, SkeletonModule, ToastModule,
    StatusBadgeComponent, ItemMasterFormComponent,
  ],
  providers: [MessageService],
  template: `
    <p-toast />

    <div class="imd">

      <!-- Back + actions bar -->
      <div class="imd__topbar">
        <p-button icon="pi pi-arrow-left" label="Back to List" [text]="true"
                  severity="secondary" size="small" (onClick)="goBack()" />
        @if (item) {
          <div class="imd__topbar-actions">
            <p-button label="Edit" icon="pi pi-pencil" severity="primary"
                      size="small" (onClick)="openEdit()" />
          </div>
        }
      </div>

      @if (loading) {
        <!-- Skeleton while loading -->
        <div class="imd__skeleton-grid">
          @for (i of [1,2,3,4,5,6]; track i) {
            <p-skeleton height="2rem" />
          }
        </div>
      } @else if (item) {

        <!-- Header card: part number + status -->
        <p-card styleClass="imd__header-card">
          <div class="imd__header-content">
            <div>
              <div class="imd__pn">{{ item.partNumber }} / {{ item.revision }}</div>
              <div class="imd__desc">{{ item.description }}</div>
            </div>
            <div class="imd__header-badges">
              <app-status-badge [status]="item.status" />
              <p-tag [value]="item.classification"
                     [severity]="classificationSeverity(item.classification)"
                     [class]="classificationClass(item.classification)" />
            </div>
          </div>
        </p-card>

        <!-- Two-column detail grid -->
        <div class="imd__grid">

          <!-- Identity & Classification -->
          <p-card header="Identity & Classification" styleClass="imd__card">
            <div class="imd__fields">
              <div class="imd__row">
                <span class="imd__label">Part Number</span>
                <span class="imd__value">{{ item.partNumber }}</span>
              </div>
              <div class="imd__row">
                <span class="imd__label">Revision</span>
                <span class="imd__value">{{ item.revision }}</span>
              </div>
              <div class="imd__row">
                <span class="imd__label">Description</span>
                <span class="imd__value">{{ item.description }}</span>
              </div>
              <div class="imd__row">
                <span class="imd__label">Classification</span>
                <span class="imd__value">
                  <p-tag [value]="item.classification"
                         [severity]="classificationSeverity(item.classification)"
                         [class]="classificationClass(item.classification)" />
                </span>
              </div>
              <div class="imd__row">
                <span class="imd__label">Make/Buy</span>
                <span class="imd__value">{{ item.makeBuyCode }}</span>
              </div>
              <div class="imd__row">
                <span class="imd__label">Traceability</span>
                <span class="imd__value">{{ item.traceabilityMethod }}</span>
              </div>
              <div class="imd__row">
                <span class="imd__label">Unit of Measure</span>
                <span class="imd__value">{{ item.unitOfMeasure }}</span>
              </div>
              @if (item.cageCode) {
                <div class="imd__row">
                  <span class="imd__label">CAGE Code</span>
                  <span class="imd__value">{{ item.cageCode }}</span>
                </div>
              }
            </div>
          </p-card>

          <!-- Quality & Compliance -->
          <p-card header="Quality & Compliance" styleClass="imd__card">
            <div class="imd__fields">
              <div class="imd__row">
                <span class="imd__label">Status</span>
                <span class="imd__value"><app-status-badge [status]="item.status" /></span>
              </div>
              <div class="imd__row">
                <span class="imd__label">Shelf Life Controlled</span>
                <span class="imd__value">{{ item.shelfLifeControlled ? 'Yes' : 'No' }}</span>
              </div>
              @if (item.shelfLifeDays) {
                <div class="imd__row">
                  <span class="imd__label">Shelf Life Days</span>
                  <span class="imd__value">{{ item.shelfLifeDays }}</span>
                </div>
              }
              @if (item.counterfeitRiskLevel) {
                <div class="imd__row">
                  <span class="imd__label">Counterfeit Risk</span>
                  <span class="imd__value">
                    <p-tag [value]="item.counterfeitRiskLevel"
                           [severity]="riskSeverity(item.counterfeitRiskLevel)" />
                  </span>
                </div>
              }
              <div class="imd__row">
                <span class="imd__label">Verification Required</span>
                <span class="imd__value">{{ item.verificationRequired ? 'Yes' : 'No' }}</span>
              </div>
              @if (item.approvedSuppliers?.length) {
                <div class="imd__row imd__row--full">
                  <span class="imd__label">Approved Suppliers</span>
                  <span class="imd__value imd__value--pre">{{ item.approvedSuppliers!.join('\n') }}</span>
                </div>
              }
            </div>
          </p-card>

          <!-- Audit -->
          <p-card header="Audit" styleClass="imd__card">
            <div class="imd__fields">
              <div class="imd__row">
                <span class="imd__label">Created By</span>
                <span class="imd__value">{{ item.createdBy }}</span>
              </div>
              <div class="imd__row">
                <span class="imd__label">Created At</span>
                <span class="imd__value">{{ item.createdAt | date:'medium' }}</span>
              </div>
              <div class="imd__row">
                <span class="imd__label">Modified By</span>
                <span class="imd__value">{{ item.modifiedBy }}</span>
              </div>
              <div class="imd__row">
                <span class="imd__label">Modified At</span>
                <span class="imd__value">{{ item.modifiedAt | date:'medium' }}</span>
              </div>
            </div>
          </p-card>

          <!-- Custom Fields (UDF) -->
          @if (udfFields.length > 0) {
            <p-card header="Custom Fields" styleClass="imd__card">
              <div class="imd__fields">
                @for (field of udfFields; track field.fieldKey) {
                  <div class="imd__row">
                    <span class="imd__label">{{ field.label }}</span>
                    <span class="imd__value">
                      {{ udfDisplayValue(field) }}
                    </span>
                  </div>
                }
              </div>
            </p-card>
          }

        </div>

      } @else {
        <div class="imd__not-found">Item not found.</div>
      }

    </div>

    <!-- Edit dialog -->
    @if (showEditDialog) {
      @defer {
        <app-item-master-form
          [visible]="showEditDialog"
          [itemId]="itemId"
          (visibleChange)="showEditDialog = $event"
          (saved)="onItemSaved($event)"
        />
      }
    }
  `,
  styles: [`
    .imd { padding: 1.25rem; }

    .imd__topbar {
      display: flex; align-items: center; justify-content: space-between;
      margin-bottom: 1rem;
    }
    .imd__topbar-actions { display: flex; gap: 0.5rem; }

    .imd__skeleton-grid {
      display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; margin-top: 1rem;
    }

    .imd__header-card { margin-bottom: 1rem; }
    .imd__header-content {
      display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem;
    }
    .imd__pn { font-size: 1.375rem; font-weight: 700; }
    .imd__desc { font-size: 0.9375rem; color: var(--p-text-muted-color); margin-top: 0.25rem; }
    .imd__header-badges { display: flex; align-items: center; gap: 0.5rem; flex-shrink: 0; }

    .imd__grid {
      display: grid; grid-template-columns: 1fr 1fr; gap: 1rem;
    }

    .imd__card { height: 100%; }

    .imd__fields { display: flex; flex-direction: column; gap: 0.625rem; }
    .imd__row {
      display: flex; gap: 0.5rem;
      padding-bottom: 0.5rem;
      border-bottom: 1px solid var(--p-surface-border);
    }
    .imd__row:last-child { border-bottom: none; padding-bottom: 0; }
    .imd__row--full { flex-direction: column; }
    .imd__label {
      min-width: 140px; font-size: 0.8125rem; font-weight: 500;
      color: var(--p-text-muted-color); flex-shrink: 0;
    }
    .imd__value { font-size: 0.9375rem; }
    .imd__value--pre { white-space: pre-line; font-size: 0.875rem; }

    .imd__not-found {
      padding: 2rem; text-align: center; color: var(--p-text-muted-color);
    }

    :host ::ng-deep .tag-raw-material { background: #0D9488 !important; color: #fff !important; }
    :host ::ng-deep .tag-service { background: #7C3AED !important; color: #fff !important; }
  `],
})
export class ItemMasterDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(ItemMasterApiService);
  private readonly udfApi = inject(UdfApiService);
  private readonly messageService = inject(MessageService);

  item: ItemMasterDto | null = null;
  udfFields: UdfFieldDefinition[] = [];
  loading = true;
  showEditDialog = false;
  itemId!: string;

  ngOnInit(): void {
    this.itemId = this.route.snapshot.paramMap.get('id')!;
    this.loadItem();
    this.udfApi.listFields('ITEM_MASTER').subscribe(fields => {
      this.udfFields = fields;
    });
  }

  private loadItem(): void {
    this.loading = true;
    this.api.getById(this.itemId).subscribe({
      next: item => { this.item = item; this.loading = false; },
      error: () => { this.item = null; this.loading = false; },
    });
  }

  goBack(): void {
    this.router.navigate(['/item-master']);
  }

  openEdit(): void {
    this.showEditDialog = true;
  }

  onItemSaved(item: ItemMasterDto): void {
    this.item = item;
    this.messageService.add({ severity: 'success', summary: 'Item saved', detail: item.partNumber });
  }

  udfDisplayValue(field: UdfFieldDefinition): string {
    const raw = this.item?.customFields?.[field.fieldKey];
    if (raw === undefined || raw === null) return '—';
    if (field.fieldType === 'BOOLEAN') return raw ? 'Yes' : 'No';
    return String(raw);
  }

  classificationSeverity(c: Classification): 'info' | 'warn' | 'secondary' | 'success' | 'danger' | undefined {
    switch (c) {
      case 'PURCHASED_PART': return 'info';
      case 'FABRICATED':     return 'warn';
      case 'ASSEMBLY':       return 'success';
      case 'COTS':           return 'secondary';
      default:               return undefined;
    }
  }

  classificationClass(c: Classification): string {
    if (c === 'RAW_MATERIAL') return 'tag-raw-material';
    if (c === 'SERVICE')      return 'tag-service';
    return '';
  }

  riskSeverity(level: string): 'info' | 'warn' | 'danger' | 'secondary' {
    switch (level) {
      case 'LOW':      return 'info';
      case 'MEDIUM':   return 'warn';
      case 'HIGH':
      case 'CRITICAL': return 'danger';
      default:         return 'secondary';
    }
  }
}
