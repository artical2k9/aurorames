import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { forkJoin, of, switchMap } from 'rxjs';
import { LucideFileDown } from '@lucide/angular';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { SelectButtonModule } from 'primeng/selectbutton';
import { SelectModule } from 'primeng/select';
import { DatePickerModule } from 'primeng/datepicker';
import { MessageModule } from 'primeng/message';
import { TreeTableModule } from 'primeng/treetable';
import { TreeNode } from 'primeng/api';
import { BreadcrumbService, StatusBadgeComponent } from '../../../../shared/ui';
import { BomApiService } from '../../services/bom-api.service';
import { ItemMasterApiService } from '../../../item-master/services/item-master-api.service';
import { BomDto, BomExplosionNode } from '../../models/bom.model';
import { ItemMasterDto } from '../../../item-master/models/item-master.model';

@Component({
  selector: 'app-bom-explosion',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterLink,
    ButtonModule, TagModule, SelectButtonModule, SelectModule, DatePickerModule,
    MessageModule, TreeTableModule,
    StatusBadgeComponent, LucideFileDown,
  ],
  template: `
    <div class="be">
      <!-- Header card -->
      @if (bom && parentItem) {
        <div class="be__header-card">
          <div class="be__header-left">
            <div class="be__label">PARENT ITEM</div>
            <a [routerLink]="['/item-master', bom.parentItemId]" class="be__link">
              {{ parentItem.partNumber }} Rev {{ parentItem.revision }}
            </a>
          </div>
          <div class="be__header-mid">
            <div class="be__label">BOM REVISION</div>
            <p-select [options]="revisionOptions" [(ngModel)]="selectedRevisionId"
                      optionLabel="label" optionValue="value"
                      styleClass="be__rev-select"
                      (onChange)="onRevisionChange($event.value)" />
            <app-status-badge [status]="bom.status" />
          </div>
          <div class="be__header-right">
            @if (bom.status === 'RELEASED' && bom.createdBy) {
              <span class="be__meta">Released by {{ bom.createdBy }}</span>
            }
            @if (nodes.length > 0) {
              <span class="be__summary">{{ flatCount }} components · {{ maxDepthSeen }} level{{ maxDepthSeen !== 1 ? 's' : '' }} deep</span>
            }
          </div>
        </div>
      }

      <!-- Explosion error -->
      @if (explosionError) {
        <p-message severity="error" [text]="explosionError" styleClass="be__error" />
      }

      <!-- Toolbar -->
      <div class="be__toolbar">
        <p-selectbutton [options]="formatOptions" [(ngModel)]="selectedFormat"
                        optionLabel="label" optionValue="value"
                        (onChange)="load()" />

        <label class="be__label-inline">As of date:</label>
        <p-datepicker [(ngModel)]="asOfDate" dateFormat="yy-mm-dd"
                      [showClear]="true" (onSelect)="load()" (onClear)="load()"
                      placeholder="Today" />

        <p-select [options]="depthOptions" [(ngModel)]="maxDepth"
                  optionLabel="label" optionValue="value"
                  (onChange)="load()" placeholder="Max depth" />

        <p-button label="Collapse All" [text]="true" size="small"
                  (onClick)="collapseAll()" />
        <p-button label="Expand All" [text]="true" size="small"
                  (onClick)="expandAll()" />

        <span class="be__spacer"></span>

        <p-button severity="secondary" size="small"
                  [disabled]="loading" (onClick)="downloadCsv()">
          <svg lucideFileDown [size]="14" [strokeWidth]="1.75"></svg>&nbsp;CSV
        </p-button>
        <p-button severity="secondary" size="small"
                  [disabled]="loading" (onClick)="downloadPdf()">
          <svg lucideFileDown [size]="14" [strokeWidth]="1.75"></svg>&nbsp;PDF
        </p-button>
      </div>

      <!-- Tree table -->
      <p-treeTable [value]="treeNodes" [loading]="loading"
                   styleClass="p-treetable-sm p-treetable-gridlines">
        <ng-template pTemplate="header">
          <tr>
            <th>Find #</th>
            <th>Part Number</th>
            <th>Rev</th>
            <th>Description</th>
            <th>Qty</th>
            <th>Make/Buy</th>
            <th>Ctft Risk</th>
            <th>Status</th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-rowNode let-rowData="rowData">
          <tr [ttRow]="rowNode">
            <td>{{ rowData['findNumber'] ?? '—' }}</td>
            <td>
              <p-treeTableToggler [rowNode]="rowNode" />
              <a [routerLink]="['/item-master', rowData['componentItemId']]" class="be__link">
                {{ rowData['partNumber'] }}
              </a>
            </td>
            <td>{{ rowData['revision'] }}</td>
            <td>{{ rowData['description'] }}</td>
            <td>{{ rowData['quantity'] ?? '—' }}</td>
            <td>{{ rowData['makeBuyCode'] ?? '—' }}</td>
            <td>
              @if (rowData['counterfeitRiskAlert']) {
                <span class="be__risk-badge">⚠ HIGH</span>
              } @else {
                —
              }
            </td>
            <td>
              <app-status-badge [status]="rowData['status'] ?? 'ACTIVE'" />
            </td>
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage">
          <tr><td colspan="8">No components in this BOM.</td></tr>
        </ng-template>
      </p-treeTable>

      <!-- Status bar -->
      <div class="be__statusbar">
        @if (nodes.length > 0) {
          {{ flatCount }} components shown ({{ topLevelCount }} top-level)
          @if (asOfDate) {
            · Effectivity: as of {{ asOfDateStr }}
          }
        }
      </div>
    </div>
  `,
  styles: [`
    .be { padding: 1.25rem; }

    .be__header-card {
      display: flex; gap: 2rem; align-items: flex-start;
      background: var(--p-surface-50); border: 1px solid var(--p-surface-border);
      border-radius: 8px; padding: 1rem; margin-bottom: 0.75rem;
    }
    .be__header-left, .be__header-mid, .be__header-right {
      display: flex; flex-direction: column; gap: 0.25rem;
    }
    .be__label { font-size: 0.7rem; font-weight: 700; text-transform: uppercase;
                 color: var(--p-text-muted-color); letter-spacing: 0.05em; }
    .be__label-inline { font-size: 0.8125rem; color: var(--p-text-muted-color); }
    .be__link { color: var(--p-primary-color); text-decoration: none; }
    .be__link:hover { text-decoration: underline; }
    .be__revision { font-weight: 600; }
    .be__summary { font-size: 0.8125rem; color: var(--p-text-muted-color); }
    .be__meta { font-size: 0.8125rem; color: var(--p-text-muted-color); }

    .be__error { margin-bottom: 0.75rem; }

    .be__toolbar {
      display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap;
      margin-bottom: 0.5rem;
    }
    .be__spacer { flex: 1; }

    .be__risk-badge {
      font-size: 0.8125rem; color: #B91C1C; font-weight: 600;
    }
    :host ::ng-deep .be__rev-select { min-width: 110px; font-size: 0.8125rem; }

    .be__statusbar {
      margin-top: 0.5rem; padding: 0.4rem 0;
      font-size: 0.8125rem; color: var(--p-text-muted-color);
      border-top: 1px solid var(--p-surface-border);
    }
  `],
})
export class BomExplosionComponent implements OnInit {
  private readonly bomApi = inject(BomApiService);
  private readonly itemApi = inject(ItemMasterApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly breadcrumbSvc = inject(BreadcrumbService);

  bomId = '';
  bom: BomDto | null = null;
  parentItem: ItemMasterDto | null = null;
  nodes: BomExplosionNode[] = [];
  treeNodes: TreeNode[] = [];
  loading = false;
  explosionError = '';
  flatCount = 0;
  topLevelCount = 0;
  maxDepthSeen = 0;

  revisionOptions: { label: string; value: string }[] = [];
  selectedRevisionId = '';

  selectedFormat: 'flat' | 'indented' = 'indented';
  asOfDate: Date | null = null;
  maxDepth = 0;

  readonly formatOptions = [
    { label: 'Indented', value: 'indented' },
    { label: 'Flat',     value: 'flat' },
  ];

  readonly depthOptions = [
    { label: 'All',  value: 0 },
    { label: '2',    value: 2 },
    { label: '3',    value: 3 },
    { label: '5',    value: 5 },
    { label: '10',   value: 10 },
  ];


  get asOfDateStr(): string {
    return this.asOfDate ? this.asOfDate.toISOString().substring(0, 10) : '';
  }

  ngOnInit(): void {
    this.bomId = this.route.snapshot.paramMap.get('bomId') ?? '';
    this.breadcrumbSvc.set([
      { label: 'Materials' },
      { label: 'Item Master', route: ['/item-master'] },
      { label: 'BOMs' },
      { label: 'Explosion' },
    ]);
    this.bomApi.getById(this.bomId).pipe(
      switchMap(bom => forkJoin({
        bom: of(bom),
        item: this.itemApi.getById(bom.parentItemId),
        revisions: this.bomApi.listForItem(bom.parentItemId),
      })),
    ).subscribe({
      next: ({ bom, item, revisions }) => {
        this.bom = bom;
        this.selectedRevisionId = bom.id;
        this.parentItem = item;
        this.revisionOptions = revisions.map(r => ({ label: 'Rev ' + r.bomRevision, value: r.id }));
        this.breadcrumbSvc.set([
          { label: 'Materials' },
          { label: 'Item Master', route: ['/item-master'] },
          { label: item.partNumber, route: ['/item-master', bom.parentItemId, 'boms'] },
          { label: 'Rev ' + bom.bomRevision, route: ['/boms', this.bomId] },
          { label: 'Explosion' },
        ]);
        this.cdr.detectChanges();
      },
    });
    this.load();
  }

  load(): void {
    this.loading = true;
    this.explosionError = '';
    const asOfDateStr = this.asOfDate ? this.asOfDate.toISOString().substring(0, 10) : undefined;
    const depth = this.maxDepth > 0 ? this.maxDepth : undefined;
    this.bomApi.explode(this.bomId, this.selectedFormat, asOfDateStr, undefined, depth).subscribe({
      next: nodes => {
        this.nodes = nodes;
        this.treeNodes = this.toTreeNodes(nodes);
        const flat = this.flattenNodes(nodes);
        this.flatCount = flat.length;
        this.topLevelCount = nodes.length;
        this.maxDepthSeen = flat.reduce((m, n) => Math.max(m, n.depth), 0);
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: (err: { status: number; error?: { message?: string } }) => {
        this.loading = false;
        this.explosionError = err.error?.message ?? 'Failed to load explosion';
        this.cdr.detectChanges();
      },
    });
  }

  onRevisionChange(bomId: string): void {
    if (bomId && bomId !== this.bomId) {
      this.router.navigate(['/boms', bomId, 'explosion']);
    }
  }

  expandAll(): void {
    this.treeNodes = this.setExpanded(this.treeNodes, true);
    this.cdr.detectChanges();
  }

  collapseAll(): void {
    this.treeNodes = this.setExpanded(this.treeNodes, false);
    this.cdr.detectChanges();
  }

  downloadCsv(): void {
    const asOf = this.asOfDate ? `&asOfDate=${this.asOfDateStr}` : '';
    const depth = this.maxDepth ? `&maxDepth=${this.maxDepth}` : '';
    window.open(`/api/v1/boms/${this.bomId}/explosion/download?format=${this.selectedFormat}&download=csv${asOf}${depth}`);
  }

  downloadPdf(): void {
    const asOf = this.asOfDate ? `&asOfDate=${this.asOfDateStr}` : '';
    const depth = this.maxDepth ? `&maxDepth=${this.maxDepth}` : '';
    window.open(`/api/v1/boms/${this.bomId}/explosion/download?format=${this.selectedFormat}&download=pdf${asOf}${depth}`);
  }

  private toTreeNodes(nodes: BomExplosionNode[]): TreeNode[] {
    return nodes.map(n => ({
      data: {
        componentItemId: n.componentItemId,
        findNumber: n.findNumber ?? '—',
        partNumber: n.partNumber,
        revision: n.revision,
        description: n.description,
        quantity: n.quantity,
        makeBuyCode: n.makeBuyCode,
        unitOfMeasure: n.unitOfMeasure,
        counterfeitRiskAlert: n.counterfeitRiskAlert,
        componentObsoleted: n.componentObsoleted,
        status: n.componentObsoleted ? 'OBSOLETE' : 'ACTIVE',
      },
      children: n.children?.length ? this.toTreeNodes(n.children) : undefined,
      expanded: true,
    }));
  }

  private setExpanded(nodes: TreeNode[], expanded: boolean): TreeNode[] {
    return nodes.map(n => ({
      ...n,
      expanded,
      children: n.children ? this.setExpanded(n.children, expanded) : undefined,
    }));
  }

  private flattenNodes(nodes: BomExplosionNode[]): BomExplosionNode[] {
    const result: BomExplosionNode[] = [];
    const stack = [...nodes];
    while (stack.length) {
      const node = stack.pop()!;
      result.push(node);
      if (node.children?.length) stack.push(...node.children);
    }
    return result;
  }
}
