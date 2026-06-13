import {
  AfterViewInit, ChangeDetectorRef, Component, DestroyRef, inject, OnInit,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { CheckboxModule } from 'primeng/checkbox';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { BreadcrumbService } from '../../../../shared/ui';
import { LabourApiService } from '../../services/labour-api.service';
import { SkillDto } from '../../models/labour.model';

@Component({
  selector: 'app-skill-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    TableModule, InputTextModule, InputNumberModule, CheckboxModule,
    ButtonModule, TagModule, DialogModule, ToastModule,
  ],
  providers: [MessageService],
  template: `
    <p-toast />

    <div class="skl">
      <div class="skl__heading">
        <h2 class="skl__title">Skills</h2>
        <p-button label="New Skill" icon="pi pi-plus" severity="primary"
                  size="small" (onClick)="openCreate()" />
      </div>

      <div class="skl__toolbar">
        <input pInputText type="text" placeholder="Search code, name or category..."
               [(ngModel)]="searchTerm" (keyup.enter)="reload()" />
        <p-button label="Search" severity="secondary" size="small" (onClick)="reload()" />
      </div>

      <p-table [value]="rows" [lazy]="true" [lazyLoadOnInit]="false" [paginator]="true"
               [rows]="pageSize" [totalRecords]="totalRecords"
               (onLazyLoad)="onLazyLoad($event)"
               [loading]="loading"
               styleClass="p-datatable-gridlines p-datatable-sm">
        <ng-template pTemplate="header">
          <tr>
            <th>Code</th>
            <th>Name</th>
            <th>Category</th>
            <th>Cert Required</th>
            <th>Validity (months)</th>
            <th>Active</th>
            <th style="width:7rem">Actions</th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-skill>
          <tr>
            <td>{{ skill.skillCode }}</td>
            <td>{{ skill.name }}</td>
            <td>{{ skill.category || '—' }}</td>
            <td>{{ skill.certificationRequired ? 'Yes' : 'No' }}</td>
            <td>{{ skill.validityMonths ?? 'Never expires' }}</td>
            <td>
              <p-tag [value]="skill.active ? 'Active' : 'Inactive'"
                     [severity]="skill.active ? 'success' : 'secondary'" />
            </td>
            <td>
              <p-button [label]="skill.active ? 'Deactivate' : 'Activate'"
                        [text]="true" size="small"
                        (onClick)="toggleActive(skill)" />
            </td>
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage">
          <tr><td colspan="7">No skills defined.</td></tr>
        </ng-template>
      </p-table>

      <p-dialog header="New Skill" [(visible)]="showCreate" [modal]="true"
                [style]="{ width: '420px' }">
        <div class="skl__form">
          <label>Skill Code *</label>
          <input pInputText [(ngModel)]="draft.skillCode" />
          <label>Name *</label>
          <input pInputText [(ngModel)]="draft.name" />
          <label>Category</label>
          <input pInputText [(ngModel)]="draft.category" />
          <label>Validity (months, empty = never expires)</label>
          <p-inputNumber [(ngModel)]="draft.validityMonths" [min]="1" [showButtons]="false" />
          <label class="skl__checkbox">
            <p-checkbox [(ngModel)]="certificationRequired" [binary]="true" />
            Certification required
          </label>
          @if (serverError) {
            <small class="skl__error">{{ serverError }}</small>
          }
        </div>
        <ng-template pTemplate="footer">
          <p-button label="Cancel" severity="secondary" size="small"
                    (onClick)="showCreate = false" />
          <p-button label="Create" severity="primary" size="small"
                    [loading]="saving" [disabled]="!draft.skillCode || !draft.name"
                    (onClick)="create()" />
        </ng-template>
      </p-dialog>
    </div>
  `,
  styles: [`
    .skl { padding: 1.25rem; }
    .skl__heading {
      display: flex; align-items: center; justify-content: space-between;
      margin-bottom: 1rem;
    }
    .skl__title { margin: 0; font-size: 1.375rem; font-weight: 700; }
    .skl__toolbar {
      display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.75rem;
    }
    .skl__form { display: flex; flex-direction: column; gap: 0.375rem; }
    .skl__form label { font-size: 0.8125rem; font-weight: 600; margin-top: 0.375rem; }
    .skl__checkbox { display: flex; align-items: center; gap: 0.5rem; }
    .skl__error { color: var(--p-red-500); }
  `],
})
export class SkillListComponent implements OnInit, AfterViewInit {
  private readonly api = inject(LabourApiService);
  private readonly messageService = inject(MessageService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly breadcrumbSvc = inject(BreadcrumbService);

  rows: SkillDto[] = [];
  totalRecords = 0;
  loading = true;
  pageSize = 20;
  currentPage = 0;
  searchTerm = '';

  showCreate = false;
  saving = false;
  serverError = '';
  draft: Partial<SkillDto> = {};
  certificationRequired = true;

  ngOnInit(): void {
    this.breadcrumbSvc.set([
      { label: 'Labour' },
      { label: 'Skills' },
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

  openCreate(): void {
    this.draft = {};
    this.certificationRequired = true;
    this.serverError = '';
    this.showCreate = true;
  }

  create(): void {
    this.saving = true;
    this.serverError = '';
    this.api.createSkill({ ...this.draft, certificationRequired: this.certificationRequired })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving = false;
          this.showCreate = false;
          this.messageService.add({ severity: 'success', summary: 'Skill created' });
          this.fetchRows();
          this.cdr.detectChanges();
        },
        error: err => {
          this.saving = false;
          this.serverError = err?.error?.error ?? 'Failed to create skill';
          this.cdr.detectChanges();
        },
      });
  }

  toggleActive(skill: SkillDto): void {
    this.api.patchSkill(skill.id, { active: !skill.active })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.messageService.add({
            severity: 'success',
            summary: skill.active ? 'Skill deactivated' : 'Skill activated',
          });
          this.fetchRows();
        },
        error: () => {
          this.messageService.add({ severity: 'error', summary: 'Update failed' });
          this.cdr.detectChanges();
        },
      });
  }

  private fetchRows(): void {
    this.loading = true;
    this.api.listSkills({
      page: this.currentPage,
      size: this.pageSize,
      search: this.searchTerm || undefined,
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: page => {
        this.rows = page.content;
        this.totalRecords = page.totalElements;
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
