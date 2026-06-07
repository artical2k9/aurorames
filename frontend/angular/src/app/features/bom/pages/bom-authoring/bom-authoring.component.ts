import {
  ChangeDetectorRef, Component, inject, OnInit,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { forkJoin, of, switchMap } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { MessageModule } from 'primeng/message';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { SelectModule } from 'primeng/select';
import { InputNumberModule } from 'primeng/inputnumber';
import { MessageService, ConfirmationService } from 'primeng/api';
import { LucideColumnsSettings } from '@lucide/angular';
import { BreadcrumbService, StatusBadgeComponent } from '../../../../shared/ui';
import { BomApiService } from '../../services/bom-api.service';
import { AddBomLineFormComponent } from '../../components/add-bom-line-form/add-bom-line-form.component';
import { BomHeaderEditDialogComponent } from '../../components/bom-header-edit-dialog/bom-header-edit-dialog.component';
import { GridPreferenceService, ColumnPickerComponent, ColumnDef } from '../../../../shared/grid';
import { DEFAULT_BOM_LINE_COLUMNS } from '../../constants/default-columns';
import { BomDto, BomLineDto } from '../../models/bom.model';
import { AsyncPipe } from '@angular/common';
import { PopoverModule } from 'primeng/popover';
import { ItemMasterApiService } from '../../../item-master/services/item-master-api.service';
import { ItemMasterDto } from '../../../item-master/models/item-master.model';

@Component({
  selector: 'app-bom-authoring',
  standalone: true,
  imports: [
    CommonModule, AsyncPipe, FormsModule, RouterLink,
    TableModule, ButtonModule, TagModule, DialogModule, MessageModule,
    ToastModule, ConfirmDialogModule, PopoverModule, SelectModule, InputNumberModule,
    StatusBadgeComponent, LucideColumnsSettings,
    AddBomLineFormComponent, BomHeaderEditDialogComponent, ColumnPickerComponent,
  ],
  providers: [
    MessageService,
    ConfirmationService,
    {
      provide: GridPreferenceService,
      useFactory: () => new GridPreferenceService('BOM_LINE', DEFAULT_BOM_LINE_COLUMNS),
    },
  ],
  template: `
    <p-toast />
    <p-confirmDialog />

    <div class="ba">
      @if (loadError) {
        <p-message severity="error" text="Failed to load BOM. Please refresh the page." />
      }
      @if (bom) {
        <!-- Header card -->
        <div class="ba__header-card">
          <div class="ba__header-left">
            <div class="ba__parent-link">
              @if (parentItem) {
                <a [routerLink]="['/item-master', bom.parentItemId]" class="ba__link">
                  {{ parentItem.partNumber }} Rev {{ parentItem.revision }}
                </a>
              }
              <button class="ba__edit-btn" (click)="showHeaderEdit = true"
                      title="Edit BOM header">✏</button>
            </div>
            <div class="ba__meta">
              <span class="ba__rev-label">BOM Rev:</span>
              <p-select [options]="revisionOptions" [(ngModel)]="selectedRevisionId"
                        optionLabel="label" optionValue="value"
                        styleClass="ba__rev-select"
                        (onChange)="onRevisionChange($event.value)" />
              <app-status-badge [status]="bom.status" />
              @if (bom.description) {
                <span class="ba__desc">{{ bom.description }}</span>
              }
            </div>
          </div>
          <div class="ba__header-right">
            @if (bom.ecoId) {
              <span class="ba__eco">ECO: {{ bom.ecoId }}</span>
            } @else {
              <span class="ba__eco ba__eco--none">No active ECO</span>
            }
          </div>
        </div>

        <!-- Toolbar -->
        @if (bom.status === 'DRAFT') {
          <div class="ba__toolbar">
            <p-button label="← Explosion View" [text]="true" size="small"
                      (onClick)="goToExplosion()" />
            <span class="ba__lines-count">BOM Lines — {{ lines.length }} component{{ lines.length !== 1 ? 's' : '' }}</span>
            <span class="ba__spacer"></span>
            <p-button label="+ Add Line" severity="primary" size="small"
                      (onClick)="addingLine = !addingLine" />
            <p-button label="Submit for Review" severity="success" size="small"
                      [loading]="releasing" (onClick)="confirmRelease()" />
            <p-button [rounded]="true" [text]="true"
                      aria-label="Customise columns"
                      (onClick)="colPickerPanel.toggle($event)">
              <svg lucideColumnsSettings [size]="16" [strokeWidth]="1.75"></svg>
            </p-button>
          </div>
        } @else {
          <div class="ba__toolbar">
            <p-button label="← Explosion View" [text]="true" size="small"
                      (onClick)="goToExplosion()" />
            <span class="ba__lines-count">BOM Lines — {{ lines.length }} component{{ lines.length !== 1 ? 's' : '' }}</span>
          </div>
        }

        <p-popover #colPickerPanel>
          <app-column-picker
            [columns]="(gridPreference.activeColumns$ | async) ?? []"
            (applied)="onColumnsApplied($event); colPickerPanel.hide()"
            (reset)="gridPreference.reset(); colPickerPanel.hide()"
          />
        </p-popover>

        <!-- Add line form -->
        @if (addingLine) {
          <app-add-bom-line-form
            [bomId]="bom.id"
            (saved)="onLineSaved($event)"
            (cancelled)="addingLine = false"
          />
        }

        <!-- Lines table -->
        <p-table [value]="lines"
                 [columns]="(gridPreference.activeColumns$ | async) ?? []"
                 dataKey="id"
                 styleClass="p-datatable-gridlines p-datatable-sm">
          <ng-template pTemplate="header" let-columns>
            <tr>
              @for (col of visibleCols(columns); track col.key) {
                <th>{{ col.label }}</th>
              }
              @if (bom.status === 'DRAFT') {
                <th style="width:8rem">Actions</th>
              }
            </tr>
          </ng-template>

          <ng-template pTemplate="body" let-line let-ri="rowIndex" let-columns="columns">
            <tr [class.ba__row--editing]="editingLineId === line.id">
              @for (col of visibleCols(columns); track col.key) {
                <td>
                  @if (editingLineId === line.id && col.key === 'quantity') {
                    <p-inputnumber [(ngModel)]="editQty" [min]="0.000001" [maxFractionDigits]="6"
                                   styleClass="ba__inline-input" />
                  } @else {
                    @switch (col.key) {
                      @case ('seq') { {{ ri + 1 }} }
                      @case ('effectiveFromDate') {
                        {{ line.effectiveFromDate ?? '—' }}
                        @if (line.effectivityMethod === 'UNIT') {
                          <span class="ba__unit-badge">(Unit)</span>
                        }
                      }
                      @case ('effectiveToDate') {
                        {{ line.effectiveToDate ?? '—' }}
                        @if (line.effectivityMethod === 'UNIT') {
                          <span class="ba__unit-badge">(Unit)</span>
                        }
                      }
                      @default { {{ lineValue(line, col.key) }} }
                    }
                  }
                </td>
              }
              @if (bom.status === 'DRAFT') {
                <td>
                  @if (editingLineId === line.id) {
                    <p-button label="Save" severity="primary" size="small"
                              [loading]="savingLine" (onClick)="saveLineEdit(line)" />
                    <p-button icon="pi pi-times" [text]="true" size="small"
                              (onClick)="cancelLineEdit()" />
                  } @else {
                    <p-button icon="pi pi-pencil" [text]="true" size="small"
                              (onClick)="startLineEdit(line)" />
                    <p-button icon="pi pi-trash" [text]="true" severity="danger" size="small"
                              (onClick)="removeLine(line)" />
                  }
                </td>
              }
            </tr>
          </ng-template>

          <ng-template pTemplate="emptymessage" let-columns>
            <tr>
              <td [attr.colspan]="visibleColCount(columns) + (bom.status === 'DRAFT' ? 1 : 0)">
                No lines added yet.
              </td>
            </tr>
          </ng-template>
        </p-table>

        <!-- Status bar -->
        <div class="ba__statusbar">
          <span>{{ lines.length }} component{{ lines.length !== 1 ? 's' : '' }}</span>
        </div>
      }

      <!-- BOM Header Edit dialog -->
      <app-bom-header-edit-dialog
        [visible]="showHeaderEdit"
        [bom]="bom"
        (visibleChange)="showHeaderEdit = $event"
        (saved)="onHeaderSaved($event)"
      />
    </div>
  `,
  styles: [`
    .ba { padding: 1.25rem; }

    .ba__header-card {
      display: flex; justify-content: space-between; align-items: flex-start;
      background: var(--p-surface-50); border: 1px solid var(--p-surface-border);
      border-radius: 8px; padding: 1rem; margin-bottom: 0.75rem;
    }
    .ba__header-left { display: flex; flex-direction: column; gap: 0.4rem; }
    .ba__parent-link { display: flex; align-items: center; gap: 0.5rem; }
    .ba__link { color: var(--p-primary-color); text-decoration: none; font-weight: 600; }
    .ba__link:hover { text-decoration: underline; }
    .ba__edit-btn {
      background: none; border: none; cursor: pointer; font-size: 0.875rem;
      color: var(--p-text-muted-color); padding: 0.1rem 0.3rem;
    }
    .ba__meta { display: flex; align-items: center; gap: 0.5rem; }
    .ba__revision { font-size: 0.875rem; color: var(--p-text-muted-color); }
    .ba__desc { font-size: 0.8125rem; color: var(--p-text-muted-color); }
    .ba__eco { font-size: 0.8125rem; color: var(--p-text-muted-color); }
    .ba__eco--none { font-style: italic; }

    .ba__toolbar {
      display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.5rem; flex-wrap: wrap;
    }
    .ba__spacer { flex: 1; }

    .ba__unit-badge {
      font-size: 0.7rem; color: var(--p-text-muted-color); display: block;
    }

    .ba__statusbar {
      margin-top: 0.5rem; padding: 0.4rem 0.75rem; font-size: 0.8125rem;
      color: var(--p-text-muted-color); border-top: 1px solid var(--p-surface-border);
    }
    .ba__rev-label { font-size: 0.8125rem; color: var(--p-text-muted-color); }
    :host ::ng-deep .ba__rev-select { min-width: 110px; font-size: 0.8125rem; }
    .ba__lines-count { font-size: 0.8125rem; font-weight: 600; }
    .ba__row--editing td { background: var(--p-surface-50); }
    :host ::ng-deep .ba__inline-input input { width: 80px; }
  `],
})
export class BomAuthoringComponent implements OnInit {
  private readonly bomApi = inject(BomApiService);
  private readonly itemApi = inject(ItemMasterApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly messageService = inject(MessageService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly breadcrumbSvc = inject(BreadcrumbService);
  readonly gridPreference = inject(GridPreferenceService);

  bomId = '';
  bom: BomDto | null = null;
  parentItem: ItemMasterDto | null = null;
  lines: BomLineDto[] = [];
  loading = false;
  loadError = false;
  releasing = false;
  savingLine = false;
  addingLine = false;
  showHeaderEdit = false;

  revisionOptions: { label: string; value: string }[] = [];
  selectedRevisionId = '';

  editingLineId: string | null = null;
  editQty: number | null = null;

  ngOnInit(): void {
    this.bomId = this.route.snapshot.paramMap.get('bomId') ?? '';
    this.breadcrumbSvc.set([
      { label: 'Engineering' },
      { label: 'Item Master', route: ['/item-master'] },
      { label: 'BOMs' },
      { label: 'Authoring' },
    ]);
    this.gridPreference.load();
    this.loadBom();
  }

  loadBom(): void {
    this.loading = true;
    this.loadError = false;
    this.bomApi.getById(this.bomId).pipe(
      switchMap(bom => forkJoin({
        bom: of(bom),
        item: this.itemApi.getById(bom.parentItemId),
        revisions: this.bomApi.listForItem(bom.parentItemId),
        lines: this.bomApi.getLines(this.bomId),
      })),
    ).subscribe({
      next: ({ bom, item, revisions, lines }) => {
        this.bom = bom;
        this.selectedRevisionId = bom.id;
        this.parentItem = item;
        this.lines = lines;
        this.revisionOptions = revisions.map(r => ({ label: 'Rev ' + r.bomRevision, value: r.id }));
        this.loading = false;
        this.breadcrumbSvc.set([
          { label: 'Engineering' },
          { label: 'Item Master', route: ['/item-master'] },
          { label: item.partNumber, route: ['/item-master', bom.parentItemId, 'boms'] },
          { label: 'Rev ' + bom.bomRevision },
        ]);
        this.cdr.detectChanges();
      },
      error: () => {
        this.loading = false;
        this.loadError = true;
        this.cdr.detectChanges();
      },
    });
  }

  onRevisionChange(bomId: string): void {
    if (bomId && bomId !== this.bomId) {
      this.router.navigate(['/boms', bomId]);
    }
  }

  onLineSaved(line: BomLineDto): void {
    this.lines = [...this.lines, line];
    this.addingLine = false;
    this.cdr.detectChanges();
  }

  removeLine(line: BomLineDto): void {
    this.bomApi.removeLine(this.bomId, line.id).subscribe({
      next: () => {
        this.lines = this.lines.filter(l => l.id !== line.id);
        this.cdr.detectChanges();
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Failed to remove line' });
      },
    });
  }

  confirmRelease(): void {
    this.confirmationService.confirm({
      message: `Release BOM Rev ${this.bom?.bomRevision}? This cannot be undone.`,
      accept: () => this.releaseBom(),
    });
  }

  releaseBom(): void {
    this.releasing = true;
    this.bomApi.release(this.bomId).subscribe({
      next: bom => {
        this.bom = bom;
        this.releasing = false;
        this.messageService.add({ severity: 'success', summary: 'BOM released' });
        this.cdr.detectChanges();
      },
      error: () => {
        this.releasing = false;
        this.messageService.add({ severity: 'error', summary: 'Release failed' });
      },
    });
  }

  startLineEdit(line: BomLineDto): void {
    this.editingLineId = line.id;
    this.editQty = line.quantity ?? null;
  }

  cancelLineEdit(): void {
    this.editingLineId = null;
    this.editQty = null;
  }

  saveLineEdit(line: BomLineDto): void {
    if (this.editQty == null) {
      return;
    }
    this.savingLine = true;
    this.bomApi.patchLine(this.bomId, line.id, { quantity: this.editQty }).subscribe({
      next: updated => {
        this.lines = this.lines.map(l => l.id === updated.id ? updated : l);
        this.editingLineId = null;
        this.editQty = null;
        this.savingLine = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.savingLine = false;
        this.messageService.add({ severity: 'error', summary: 'Failed to save line' });
      },
    });
  }

  goToExplosion(): void {
    this.router.navigate(['/boms', this.bomId, 'explosion']);
  }

  onHeaderSaved(bom: BomDto): void {
    this.bom = bom;
    this.showHeaderEdit = false;
    this.messageService.add({ severity: 'success', summary: 'BOM header updated' });
    this.cdr.detectChanges();
  }

  onColumnsApplied(columns: ColumnDef[]): void {
    this.gridPreference.apply(columns);
  }

  visibleCols(columns: ColumnDef[]): ColumnDef[] {
    return columns.filter(c => c.visible).sort((a, b) => a.order - b.order);
  }

  visibleColCount(columns: ColumnDef[]): number {
    return this.visibleCols(columns).length;
  }

  lineValue(line: BomLineDto, key: string): string | number {
    const val = (line as unknown as Record<string, unknown>)[key];
    return val != null ? String(val) : '—';
  }
}
