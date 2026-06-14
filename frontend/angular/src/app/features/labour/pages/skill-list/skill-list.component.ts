import {
  AfterViewInit, ChangeDetectorRef, Component, DestroyRef, inject, OnInit,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, map, of } from 'rxjs';
import { AsyncPipe, CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { CheckboxModule } from 'primeng/checkbox';
import { ButtonModule } from 'primeng/button';
import { PopoverModule } from 'primeng/popover';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { LucideColumnsSettings, LucidePencil } from '@lucide/angular';
import { GridPreferenceService, ColumnPickerComponent, ColumnDef } from '../../../../shared/grid';
import { BreadcrumbService } from '../../../../shared/ui';
import { UdfApiService } from '../../../../shared/udf/udf-api.service';
import { LabourApiService } from '../../services/labour-api.service';
import { DEFAULT_SKILL_COLUMNS } from '../../constants/default-columns';
import { SkillDto } from '../../models/labour.model';

@Component({
  selector: 'app-skill-list',
  standalone: true,
  imports: [
    CommonModule, AsyncPipe, FormsModule,
    TableModule, InputTextModule, InputNumberModule, CheckboxModule,
    ButtonModule, PopoverModule, TagModule, DialogModule, ToastModule,
    ColumnPickerComponent, LucideColumnsSettings, LucidePencil,
  ],
  providers: [
    MessageService,
    {
      provide: GridPreferenceService,
      useFactory: () => new GridPreferenceService('SKILL', DEFAULT_SKILL_COLUMNS),
    },
  ],
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

      <p-table [columns]="(gridPreference.activeColumns$ | async) ?? []"
               [value]="rows" [lazy]="true" [lazyLoadOnInit]="false" [paginator]="true"
               [rows]="pageSize" [totalRecords]="totalRecords"
               (onLazyLoad)="onLazyLoad($event)"
               [loading]="loading"
               styleClass="p-datatable-gridlines p-datatable-sm">
        <ng-template pTemplate="header" let-columns>
          <tr>
            @for (col of visibleColumns(columns); track col.key) {
              <th>{{ col.label }}</th>
            }
            <th style="width:10rem">Actions</th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-skill let-columns="columns">
          <tr>
            @for (col of visibleColumns(columns); track col.key) {
              <td>
                @switch (col.key) {
                  @case ('active') {
                    <p-tag [value]="skill.active ? 'Active' : 'Inactive'"
                           [severity]="skill.active ? 'success' : 'secondary'" />
                  }
                  @case ('certificationRequired') {
                    {{ skill.certificationRequired ? 'Yes' : 'No' }}
                  }
                  @case ('validityMonths') {
                    {{ skill.validityMonths ?? 'Never expires' }}
                  }
                  @default {
                    {{ getCellValue(skill, col) ?? '—' }}
                  }
                }
              </td>
            }
            <td>
              <p-button [rounded]="true" [text]="true" size="small"
                        title="Edit" aria-label="Edit" (onClick)="openEdit(skill)">
                <svg lucidePencil [size]="16" [strokeWidth]="1.75"></svg>
              </p-button>
              <p-button [label]="skill.active ? 'Deactivate' : 'Activate'"
                        [text]="true" size="small"
                        (onClick)="toggleActive(skill)" />
            </td>
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage" let-columns>
          <tr><td [attr.colspan]="visibleColumnCount(columns) + 1">No skills defined.</td></tr>
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

      <p-dialog header="Edit Skill" [(visible)]="showEdit" [modal]="true"
                [style]="{ width: '420px' }">
        <div class="skl__form">
          <label>Skill Code</label>
          <input pInputText [value]="editDraft.skillCode" disabled />
          <label>Name *</label>
          <input pInputText [(ngModel)]="editDraft.name" />
          <label>Category</label>
          <input pInputText [(ngModel)]="editDraft.category" />
          <label>Validity (months, empty = never expires)</label>
          <p-inputNumber [(ngModel)]="editDraft.validityMonths" [min]="1" [showButtons]="false" />
          <label class="skl__checkbox">
            <p-checkbox [(ngModel)]="editCertRequired" [binary]="true" />
            Certification required
          </label>
          @if (editError) {
            <small class="skl__error">{{ editError }}</small>
          }
        </div>
        <ng-template pTemplate="footer">
          <p-button label="Cancel" severity="secondary" size="small"
                    (onClick)="showEdit = false" />
          <p-button label="Save" severity="primary" size="small"
                    [loading]="savingEdit" [disabled]="!editDraft.name"
                    (onClick)="saveEdit()" />
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
  private readonly udfApi = inject(UdfApiService);
  readonly gridPreference = inject(GridPreferenceService);
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

  showEdit = false;
  savingEdit = false;
  editError = '';
  editId = '';
  editDraft: Partial<SkillDto> = {};
  editCertRequired = true;

  ngOnInit(): void {
    this.breadcrumbSvc.set([
      { label: 'Labour' },
      { label: 'Skills' },
    ]);
    this.udfApi.listFields('SKILL').pipe(
      map(udfs => udfs.map((u, i): ColumnDef => ({
        key: u.fieldKey,
        label: u.label,
        visible: false,
        order: DEFAULT_SKILL_COLUMNS.length + i,
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

  onColumnsApplied(columns: ColumnDef[]): void {
    this.gridPreference.apply(columns);
  }

  getCellValue(skill: SkillDto, col: ColumnDef): unknown {
    if (!col.udf) return (skill as unknown as Record<string, unknown>)[col.key];
    const direct = (skill as unknown as Record<string, unknown>)[col.key];
    return direct !== undefined ? direct : skill.customFields?.[col.key];
  }

  visibleColumns(columns: ColumnDef[]): ColumnDef[] {
    return columns.filter(c => c.visible).sort((a, b) => a.order - b.order);
  }

  visibleColumnCount(columns: ColumnDef[]): number {
    return this.visibleColumns(columns).length;
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

  openEdit(skill: SkillDto): void {
    this.editId = skill.id;
    this.editDraft = {
      skillCode: skill.skillCode,
      name: skill.name,
      category: skill.category,
      validityMonths: skill.validityMonths,
    };
    this.editCertRequired = skill.certificationRequired;
    this.editError = '';
    this.showEdit = true;
  }

  saveEdit(): void {
    this.savingEdit = true;
    this.editError = '';
    this.api.patchSkill(this.editId, {
      name: this.editDraft.name,
      category: this.editDraft.category,
      validityMonths: this.editDraft.validityMonths,
      certificationRequired: this.editCertRequired,
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.savingEdit = false;
        this.showEdit = false;
        this.messageService.add({ severity: 'success', summary: 'Skill updated' });
        this.fetchRows();
        this.cdr.detectChanges();
      },
      error: err => {
        this.savingEdit = false;
        this.editError = err?.error?.error ?? 'Failed to update skill';
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
