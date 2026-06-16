import {
  AfterViewInit, ChangeDetectorRef, Component, DestroyRef, inject, OnInit,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  Subject, debounceTime, distinctUntilChanged, switchMap, catchError, map, of,
} from 'rxjs';
import { AsyncPipe, CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { InputTextModule } from 'primeng/inputtext';
import { AutoCompleteModule, AutoCompleteSelectEvent } from 'primeng/autocomplete';
import { ButtonModule } from 'primeng/button';
import { PopoverModule } from 'primeng/popover';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import {
  LucideColumnsSettings, LucideView, LucideGitBranch, LucideLock, LucideLockOpen,
} from '@lucide/angular';
import { revisionLabel } from '../../constants/revision-label';
import { GridPreferenceService, ColumnPickerComponent, ColumnDef } from '../../../../shared/grid';
import { BreadcrumbService } from '../../../../shared/ui';
import { UdfApiService } from '../../../../shared/udf/udf-api.service';
import { ItemMasterApiService } from '../../../item-master/services/item-master-api.service';
import { ItemMasterDto } from '../../../item-master/models/item-master.model';
import { RoutingApiService } from '../../services/routing-api.service';
import { ReferenceDataApiService } from '../../services/reference-data-api.service';
import { DEFAULT_ROUTE_COLUMNS } from '../../constants/default-columns';
import { RouteDto, RouteStatus, RouteTypeDto } from '../../models/routing.model';

@Component({
  selector: 'app-route-list',
  standalone: true,
  imports: [
    CommonModule, AsyncPipe, FormsModule,
    TableModule, InputTextModule, AutoCompleteModule, ButtonModule,
    PopoverModule, TagModule, DialogModule, ToastModule,
    ColumnPickerComponent, LucideColumnsSettings, LucideView, LucideGitBranch,
    LucideLock, LucideLockOpen,
  ],
  providers: [
    MessageService,
    {
      provide: GridPreferenceService,
      useFactory: () => new GridPreferenceService('ROUTING', DEFAULT_ROUTE_COLUMNS),
    },
  ],
  template: `
    <p-toast />

    <div class="rl">
      <div class="rl__heading">
        <div class="rl__heading-left">
          <h2 class="rl__title">Routes</h2>
          <span class="rl__count">({{ totalRecords }} routes)</span>
        </div>
        <p-button label="New Route" icon="pi pi-plus" severity="primary"
                  size="small" (onClick)="openCreate()" />
      </div>

      <div class="rl__toolbar">
        <input pInputText type="text" placeholder="Search part..."
               [(ngModel)]="searchTerm" (ngModelChange)="onSearchChange()" />
        <p-button label="Clear" severity="secondary" size="small" (onClick)="clearFilters()" />
        <p-button [rounded]="true" [text]="true"
                  aria-label="Customise columns" (onClick)="colPickerPanel.toggle($event)">
          <svg lucideColumnsSettings [size]="16" [strokeWidth]="1.75"></svg>
        </p-button>
      </div>

      <p-popover #colPickerPanel>
        <app-column-picker
          [columns]="(gridPreference.activeColumns$ | async) ?? []"
          (applied)="onColumnsApplied($event); colPickerPanel.hide()"
          (cancelled)="colPickerPanel.hide()"
          (reset)="gridPreference.reset(); colPickerPanel.hide()"
        />
      </p-popover>

      <p-table
        [columns]="(gridPreference.activeColumns$ | async) ?? []"
        [value]="rows"
        [lazy]="true"
        [lazyLoadOnInit]="false"
        [paginator]="true"
        [rows]="pageSize"
        [totalRecords]="totalRecords"
        (onLazyLoad)="onLazyLoad($event)"
        [loading]="loading"
        dataKey="id"
        styleClass="p-datatable-gridlines p-datatable-sm"
      >
        <ng-template pTemplate="header" let-columns>
          <tr>
            @for (col of visibleColumns(columns); track col.key) {
              <th>{{ col.label }}</th>
            }
            <th style="width:6rem">Actions</th>
          </tr>
        </ng-template>

        <ng-template pTemplate="body" let-route let-columns="columns">
          <tr>
            @for (col of visibleColumns(columns); track col.key) {
              <td>
                @switch (col.key) {
                  @case ('status') {
                    <span class="rl__statuscell">
                      <p-tag [value]="statusLabel(route.status)"
                             [severity]="statusSeverity(route.status)" />
                      @if (route.lockHolder) {
                        <svg lucideLock [size]="14" [strokeWidth]="2" class="rl__lockicon"
                             [attr.title]="'Locked by ' + (isLockedByMe(route) ? 'you' : route.lockHolder)"></svg>
                      }
                    </span>
                  }
                  @case ('lockHolder') {
                    @if (route.lockHolder) {
                      <span class="rl__lockedby">
                        <svg lucideLock [size]="14" [strokeWidth]="2"></svg>
                        {{ isLockedByMe(route) ? 'You' : route.lockHolder }}
                      </span>
                    } @else {
                      <span class="rl__unlocked">
                        <svg lucideLockOpen [size]="14" [strokeWidth]="2"></svg> —
                      </span>
                    }
                  }
                  @default {
                    {{ getCellValue(route, col) ?? '—' }}
                  }
                }
              </td>
            }
            <td>
              <p-button [rounded]="true" [text]="true" size="small"
                        title="View" aria-label="View"
                        (onClick)="navigateToDetail(route.id)">
                <svg lucideView [size]="16" [strokeWidth]="1.75"></svg>
              </p-button>
              <p-button [rounded]="true" [text]="true" size="small"
                        [title]="canRevise(route) ? 'Create revision' : 'Create revision (available once approved/rejected)'"
                        aria-label="Create revision"
                        [disabled]="working || !canRevise(route)" (onClick)="createRevision(route)">
                <svg lucideGitBranch [size]="16" [strokeWidth]="1.75"></svg>
              </p-button>
            </td>
          </tr>
        </ng-template>

        <ng-template pTemplate="emptymessage" let-columns>
          <tr>
            <td [attr.colspan]="visibleColumnCount(columns) + 1">No routes found.</td>
          </tr>
        </ng-template>
      </p-table>

      <p-dialog header="New Route" [(visible)]="showCreate" [modal]="true"
                [style]="{ width: '460px' }">
        <div class="rl__form">
          <label>Part Number *</label>
          <p-autocomplete [(ngModel)]="partNumberModel"
                          [suggestions]="itemSuggestions"
                          field="partNumber"
                          placeholder="Search approved part number..."
                          [dropdown]="false"
                          [minLength]="1"
                          (completeMethod)="onSearchItem($event)"
                          (onSelect)="onItemSelect($event)">
            <ng-template #item let-item>
              <div class="rl__suggestion">
                <span class="rl__suggestion-pn">{{ item.partNumber }}</span>
                <span class="rl__suggestion-rev">Rev {{ item.revision }}</span>
              </div>
            </ng-template>
          </p-autocomplete>
          <label>Part Revision *</label>
          <input pInputText [(ngModel)]="draft.partRevision" placeholder="e.g. A" />
          <label>Route Type *</label>
          <select [(ngModel)]="draft.routeTypeId" class="rl__select">
            <option [ngValue]="undefined" disabled>Select route type…</option>
            @for (rt of routeTypes; track rt.id) {
              <option [ngValue]="rt.id">{{ rt.name }}{{ rt.isStandard ? ' (Standard)' : '' }}</option>
            }
          </select>
          <label>Reason for Revision *</label>
          <input pInputText [(ngModel)]="draft.reasonForRevision" placeholder="e.g. Initial release" />
          @if (serverError) {
            <small class="rl__error">{{ serverError }}</small>
          }
        </div>
        <ng-template pTemplate="footer">
          <p-button label="Cancel" severity="secondary" size="small"
                    (onClick)="showCreate = false" />
          <p-button label="Create" severity="primary" size="small"
                    [loading]="saving" [disabled]="!canSave()"
                    (onClick)="create()" />
        </ng-template>
      </p-dialog>
    </div>
  `,
  styles: [`
    .rl { padding: 1.25rem; }
    .rl__heading {
      display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 1rem;
    }
    .rl__heading-left { display: flex; align-items: baseline; gap: 0.5rem; }
    .rl__title { margin: 0; font-size: 1.375rem; font-weight: 700; }
    .rl__count { font-size: 0.875rem; color: var(--p-text-muted-color); }
    .rl__toolbar {
      display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; margin-bottom: 0.75rem;
    }
    .rl__form { display: flex; flex-direction: column; gap: 0.375rem; }
    .rl__form label { font-size: 0.8125rem; font-weight: 600; margin-top: 0.375rem; }
    .rl__select { padding: 0.4rem 0.5rem; border: 1px solid var(--p-inputtext-border-color); border-radius: 4px; }
    .rl__error { color: var(--p-red-500); }
    .rl__statuscell { display: inline-flex; align-items: center; gap: 0.4rem; }
    .rl__lockicon { color: var(--p-orange-500); }
    .rl__lockedby { display: inline-flex; align-items: center; gap: 0.3rem; color: var(--p-orange-500); }
    .rl__unlocked { display: inline-flex; align-items: center; gap: 0.3rem; color: var(--p-text-muted-color); }
    .rl__suggestion { display: flex; align-items: baseline; gap: 0.5rem; }
    .rl__suggestion-pn { font-weight: 600; }
    .rl__suggestion-rev { font-size: 0.75rem; color: var(--p-text-muted-color); }
    :host ::ng-deep .rl__form p-autocomplete,
    :host ::ng-deep .rl__form .p-autocomplete { width: 100%; }
    :host ::ng-deep .rl__form .p-autocomplete-input { width: 100%; }
  `],
})
export class RouteListComponent implements OnInit, AfterViewInit {
  private readonly api            = inject(RoutingApiService);
  private readonly refApi         = inject(ReferenceDataApiService);
  private readonly router         = inject(Router);
  private readonly messageService = inject(MessageService);
  private readonly destroyRef     = inject(DestroyRef);
  private readonly cdr            = inject(ChangeDetectorRef);
  private readonly udfApi         = inject(UdfApiService);
  private readonly itemApi        = inject(ItemMasterApiService);
  readonly gridPreference         = inject(GridPreferenceService);
  private readonly breadcrumbSvc  = inject(BreadcrumbService);

  rows: RouteDto[] = [];
  totalRecords = 0;
  loading = true;
  pageSize = 20;
  currentPage = 0;

  searchTerm = '';

  showCreate = false;
  saving = false;
  serverError = '';
  draft: { partId?: string; partRevision?: string; routeTypeId?: string; reasonForRevision?: string } = {};

  routeTypes: RouteTypeDto[] = [];
  private routeTypeCodeById = new Map<string, string>();
  private partNumberById = new Map<string, string>();
  working = false;

  itemSuggestions: ItemMasterDto[] = [];
  partNumberModel: ItemMasterDto | string = '';

  private readonly searchSubject = new Subject<string>();
  private readonly searchItemSubject = new Subject<string>();

  ngOnInit(): void {
    this.breadcrumbSvc.set([{ label: 'Engineering' }, { label: 'Routes' }]);

    this.refApi.listRouteTypes().pipe(
      catchError(() => of([] as RouteTypeDto[])),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(types => {
      this.routeTypes = types.filter(t => t.active);
      this.routeTypeCodeById = new Map(types.map(t => [t.id as string, t.code]));
      this.cdr.detectChanges();
    });

    this.udfApi.listFields('ROUTING').pipe(
      map(udfs => udfs.map((u, i): ColumnDef => ({
        key: u.fieldKey,
        label: u.label,
        visible: false,
        order: DEFAULT_ROUTE_COLUMNS.length + i,
        udf: true,
      }))),
      catchError(() => of([])),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: udfCols => {
        this.gridPreference.load(udfCols);
        this.cdr.detectChanges();
      },
    });

    this.searchSubject.pipe(
      debounceTime(400),
      distinctUntilChanged(),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => {
      this.currentPage = 0;
      this.fetchRows();
    });

    this.searchItemSubject.pipe(
      debounceTime(250),
      distinctUntilChanged(),
      switchMap(q => this.itemApi.list({ search: q, page: 0, size: 10, revisionStatus: 'APPROVED' })
        .pipe(catchError(() => of({ content: [], totalElements: 0 })))),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(page => {
      this.itemSuggestions = page.content;
      this.cdr.detectChanges();
    });
  }

  ngAfterViewInit(): void {
    setTimeout(() => this.fetchRows());
  }

  onLazyLoad(event: TableLazyLoadEvent): void {
    this.currentPage = (event.first ?? 0) / (event.rows ?? this.pageSize);
    this.pageSize = event.rows ?? this.pageSize;
    this.fetchRows();
  }

  onSearchChange(): void {
    this.searchSubject.next(this.searchTerm);
  }

  clearFilters(): void {
    this.searchTerm = '';
    this.currentPage = 0;
    this.fetchRows();
  }

  onColumnsApplied(columns: ColumnDef[]): void {
    this.gridPreference.apply(columns);
  }

  getCellValue(route: RouteDto, col: ColumnDef): unknown {
    if (col.key === 'partNumber') return this.partNumberById.get(route.partId) ?? '…';
    if (col.key === 'routeTypeCode') return this.routeTypeCodeById.get(route.routeTypeId) ?? '—';
    if (col.key === 'revision') return revisionLabel(route.revision);
    if (!col.udf) return (route as unknown as Record<string, unknown>)[col.key];
    const direct = (route as unknown as Record<string, unknown>)[col.key];
    return direct !== undefined ? direct : route.customFields?.[col.key];
  }

  visibleColumns(columns: ColumnDef[]): ColumnDef[] {
    return columns.filter(c => c.visible).sort((a, b) => a.order - b.order);
  }

  visibleColumnCount(columns: ColumnDef[]): number {
    return this.visibleColumns(columns).length;
  }

  statusLabel(status: RouteStatus): string {
    switch (status) {
      case 'APPROVED': return 'Approved';
      case 'PENDING_APPROVAL': return 'Pending';
      case 'REJECTED': return 'Rejected';
      default: return 'Draft';
    }
  }

  statusSeverity(status: RouteStatus): 'success' | 'warn' | 'danger' | 'secondary' {
    switch (status) {
      case 'APPROVED': return 'success';
      case 'PENDING_APPROVAL': return 'warn';
      case 'REJECTED': return 'danger';
      default: return 'secondary';
    }
  }

  navigateToDetail(id: string): void {
    this.router.navigate(['/routing', id]);
  }

  /** A revision can only be started from a fully-actioned route (matches the route-detail rule). */
  canRevise(route: RouteDto): boolean {
    return route.status === 'APPROVED' || route.status === 'REJECTED';
  }

  /** Current user's KC subject, decoded from the access token (matches RouteLockGuard). */
  private currentSubject(): string | null {
    const token = localStorage.getItem('access_token');
    if (!token) return null;
    try {
      const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
      return (payload.sub as string) ?? (payload.preferred_username as string) ?? null;
    } catch {
      return null;
    }
  }

  isLockedByMe(route: RouteDto): boolean {
    return !!route.lockHolder && route.lockHolder === this.currentSubject();
  }

  openCreate(): void {
    this.draft = {};
    this.partNumberModel = '';
    this.itemSuggestions = [];
    this.serverError = '';
    this.showCreate = true;
  }

  onSearchItem(event: { query: string }): void {
    this.draft.partId = undefined;
    this.searchItemSubject.next(event.query);
  }

  onItemSelect(event: AutoCompleteSelectEvent): void {
    const item = event.value as ItemMasterDto;
    this.draft.partId = item.id;
    if (!this.draft.partRevision) this.draft.partRevision = String(item.revision ?? '');
  }

  canSave(): boolean {
    return !!(this.draft.partId && this.draft.partRevision
      && this.draft.routeTypeId && this.draft.reasonForRevision);
  }

  create(): void {
    this.saving = true;
    this.serverError = '';
    this.api.create({
      partId: this.draft.partId as string,
      partRevision: this.draft.partRevision as string,
      routeTypeId: this.draft.routeTypeId as string,
      reasonForRevision: this.draft.reasonForRevision as string,
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: created => {
        this.saving = false;
        this.showCreate = false;
        this.messageService.add({ severity: 'success', summary: 'Route created' });
        this.cdr.detectChanges();
        this.navigateToDetail(created.id);
      },
      error: err => {
        this.saving = false;
        this.serverError = err?.error?.error ?? 'Failed to create route';
        this.cdr.detectChanges();
      },
    });
  }

  private fetchRows(): void {
    this.loading = true;
    this.api.list({
      page: this.currentPage,
      size: this.pageSize,
      search: this.searchTerm || undefined,
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: page => {
        this.rows = page.content;
        this.totalRecords = page.totalElements;
        this.loading = false;
        this.resolvePartNumbers(page.content);
        this.cdr.detectChanges();
      },
      error: () => {
        this.rows = [];
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }

  /** Resolve partId → partNumber for the current page (routing-service stores only partId). */
  private resolvePartNumbers(rows: RouteDto[]): void {
    const ids = [...new Set(rows.map(r => r.partId).filter(id => !this.partNumberById.has(id)))];
    ids.forEach(id => this.itemApi.getById(id).pipe(
      catchError(() => of(null)), takeUntilDestroyed(this.destroyRef),
    ).subscribe(item => {
      this.partNumberById.set(id, item?.partNumber ?? id);
      this.cdr.detectChanges();
    }));
  }

  createRevision(route: RouteDto): void {
    this.working = true;
    this.api.startRouteRevision(route.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.working = false;
        this.messageService.add({ severity: 'success', summary: 'New revision started' });
        this.cdr.detectChanges();
        this.navigateToDetail(route.id);
      },
      error: err => {
        this.working = false;
        this.messageService.add({ severity: 'error', summary: err?.error?.error ?? 'Failed to start revision' });
        this.cdr.detectChanges();
      },
    });
  }
}
