import { AfterViewInit, ChangeDetectorRef, Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { MessageModule } from 'primeng/message';
import { BreadcrumbService } from '../../../../shared/ui';
import { ItemMasterApiService } from '../../../item-master/services/item-master-api.service';
import { ItemMasterDto } from '../../../item-master/models/item-master.model';

@Component({
  selector: 'app-bom-browser',
  standalone: true,
  imports: [
    FormsModule,
    TableModule, ButtonModule, InputTextModule, MessageModule,
  ],
  template: `
    <div class="bb">

      <div class="bb__heading">
        <h2 class="bb__title">Bills of Materials</h2>
        <span class="bb__subtitle">Select an item to view or author its BOMs</span>
      </div>

      <div class="bb__toolbar">
        <input pInputText
               class="bb__search"
               placeholder="Search by part number or description…"
               [(ngModel)]="searchTerm"
               (ngModelChange)="onSearch()" />
      </div>

      @if (error) {
        <p-message severity="error" [text]="error" styleClass="bb__error" />
      }

      <p-table [value]="items"
               [loading]="loading"
               [paginator]="true"
               [rows]="pageSize"
               [totalRecords]="totalRecords"
               [lazy]="true"
               [lazyLoadOnInit]="false"
               (onLazyLoad)="onLazyLoad($event)"
               [rowHover]="true"
               styleClass="bb__table">
        <ng-template pTemplate="header">
          <tr>
            <th>Part Number</th>
            <th>Rev</th>
            <th>Description</th>
            <th>Classification</th>
            <th>Status</th>
            <th></th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-item>
          <tr class="bb__row" (click)="openBoms(item)">
            <td class="bb__pn">{{ item.partNumber }}</td>
            <td>{{ item.revision }}</td>
            <td>{{ item.description }}</td>
            <td>{{ item.classification }}</td>
            <td>{{ item.status }}</td>
            <td class="bb__action">
              <p-button label="View BOMs"
                        severity="secondary"
                        size="small"
                        (click)="$event.stopPropagation(); openBoms(item)" />
            </td>
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage">
          <tr>
            <td colspan="6" class="bb__empty">No items found.</td>
          </tr>
        </ng-template>
      </p-table>
    </div>
  `,
  styles: [`
    .bb {
      padding: 1.5rem;
      max-width: 1200px;
    }

    .bb__heading {
      margin-bottom: 1.25rem;
    }

    .bb__title {
      margin: 0 0 0.25rem;
      font-size: 1.375rem;
      font-weight: 700;
      color: var(--aurora-text-primary);
    }

    .bb__subtitle {
      font-size: 0.875rem;
      color: var(--aurora-text-secondary);
    }

    .bb__toolbar {
      margin-bottom: 1rem;
    }

    .bb__search {
      width: 320px;
    }

    .bb__error {
      margin-bottom: 1rem;
    }

    .bb__table {
      width: 100%;
    }

    .bb__row {
      cursor: pointer;
    }

    .bb__pn {
      font-weight: 600;
      font-family: monospace;
    }

    .bb__action {
      text-align: right;
    }

    .bb__empty {
      text-align: center;
      padding: 2rem;
      color: var(--aurora-text-secondary);
    }
  `],
})
export class BomBrowserComponent implements AfterViewInit {
  private readonly itemApi    = inject(ItemMasterApiService);
  private readonly router     = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly cdr        = inject(ChangeDetectorRef);

  constructor() {
    inject(BreadcrumbService).set([
      { label: 'Engineering' },
      { label: 'BOMs' },
    ]);
  }

  items: ItemMasterDto[] = [];
  loading = true;
  error = '';
  searchTerm = '';
  pageSize = 10;
  totalRecords = 0;

  private searchDebounce?: ReturnType<typeof setTimeout>;

  ngAfterViewInit(): void {
    // lazyLoadOnInit="false" on the table suppresses PrimeNG's auto-emit from
    // ngOnInit. Initial load is owned here, deferred past the first CD pass.
    setTimeout(() => this.load(0));
  }

  onLazyLoad(event: TableLazyLoadEvent): void {
    const first = event.first ?? 0;
    const rows = event.rows ?? this.pageSize;
    const page = Math.floor(first / rows);
    this.pageSize = rows;
    this.load(page);
  }

  onSearch(): void {
    clearTimeout(this.searchDebounce);
    this.searchDebounce = setTimeout(() => this.load(0), 350);
  }

  openBoms(item: ItemMasterDto): void {
    this.router.navigate(['/bom', item.id]);
  }

  private load(page: number): void {
    this.loading = true;
    this.error = '';
    this.itemApi.list({ page, size: this.pageSize, search: this.searchTerm || undefined })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => {
          this.items = result.content;
          this.totalRecords = result.totalElements;
          this.loading = false;
          this.cdr.detectChanges();
        },
        error: () => {
          this.error = 'Failed to load items. Please try again.';
          this.loading = false;
          this.cdr.detectChanges();
        },
      });
  }
}
