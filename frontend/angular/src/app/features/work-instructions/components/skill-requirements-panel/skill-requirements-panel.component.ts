import {
  ChangeDetectorRef, Component, DestroyRef, EventEmitter, inject, Input, OnChanges, Output,
  SimpleChanges,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { WorkInstructionApiService } from '../../services/work-instruction-api.service';
import {
  QualificationResultDto, SkillRequirementDto,
} from '../../models/work-instruction.model';

interface SkillOption { label: string; value: string; }
interface LabourSkill { id: string; skillCode: string; name: string; }
interface LabourSkillsPage { content: LabourSkill[]; }

/**
 * Skill requirements panel: add/remove the skills required to perform the instruction (picked from
 * labour-service), plus an on-demand qualification preview for an operator. Adding/removing targets
 * the editable draft, so the parent reloads on change.
 */
@Component({
  selector: 'app-skill-requirements-panel',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TableModule, ButtonModule, SelectModule,
    InputTextModule, TagModule, ToastModule,
  ],
  providers: [MessageService],
  template: `
    <p-toast />
    <div class="srp">
      <h3 class="srp__title">Skill Requirements ({{ rows.length }})</h3>

      @if (editable) {
        <div class="srp__add">
          <p-select [options]="skillOptions" [(ngModel)]="selectedSkillId"
                    optionLabel="label" optionValue="value" [filter]="true"
                    placeholder="Select a skill…" styleClass="srp__select" />
          <p-button label="Add" size="small" [disabled]="!selectedSkillId"
                    [loading]="adding" (onClick)="add()" />
        </div>
      }

      <p-table [value]="rows" dataKey="id" styleClass="p-datatable-gridlines p-datatable-sm">
        <ng-template pTemplate="header">
          <tr>
            <th style="width:9rem">Code</th>
            <th>Skill</th>
            @if (editable) { <th style="width:5rem">Actions</th> }
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-r>
          <tr>
            <td>{{ r.skillCode }}</td>
            <td>{{ r.skillName }}</td>
            @if (editable) {
              <td>
                <p-button [rounded]="true" [text]="true" size="small" icon="pi pi-trash"
                          severity="danger" title="Remove" (onClick)="remove(r)" />
              </td>
            }
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage">
          <tr><td [attr.colspan]="editable ? 3 : 2">No skill requirements.</td></tr>
        </ng-template>
      </p-table>

      <div class="srp__qual">
        <h4 class="srp__qual-title">Qualification check</h4>
        <div class="srp__qual-row">
          <input pInputText placeholder="Employee ID (UUID)" [(ngModel)]="employeeId" />
          <p-button label="Check" size="small" severity="secondary"
                    [disabled]="!employeeId" [loading]="checking" (onClick)="checkQualification()" />
        </div>
        @if (qualification) {
          <div class="srp__qual-result">
            <p-tag [value]="qualLabel()" [severity]="qualSeverity()" />
            @if (qualification.reason === 'VERIFICATION_UNAVAILABLE') {
              <span class="srp__qual-note">Labour service unavailable — treated as not qualified.</span>
            }
            @if (qualification.missing.length) {
              <ul class="srp__missing">
                @for (m of qualification.missing; track m.skillId) {
                  <li>{{ m.skillCode }} — {{ m.state }}</li>
                }
              </ul>
            }
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .srp { margin-top: 1.25rem; }
    .srp__title { margin: 0 0 0.5rem; font-size: 1.05rem; font-weight: 600; }
    .srp__add { display: flex; gap: 0.5rem; margin-bottom: 0.5rem; align-items: center; }
    :host ::ng-deep .srp__select { min-width: 280px; }
    .srp__qual { margin-top: 1rem; }
    .srp__qual-title { margin: 0 0 0.375rem; font-size: 0.95rem; font-weight: 600; }
    .srp__qual-row { display: flex; gap: 0.5rem; align-items: center; }
    .srp__qual-result { margin-top: 0.5rem; display: flex; flex-direction: column; gap: 0.25rem; }
    .srp__qual-note { font-size: 0.8125rem; color: var(--p-text-muted-color); }
    .srp__missing { margin: 0.25rem 0 0; padding-left: 1.25rem; font-size: 0.8125rem; }
  `],
})
export class SkillRequirementsPanelComponent implements OnChanges {
  private readonly api            = inject(WorkInstructionApiService);
  private readonly http           = inject(HttpClient);
  private readonly messageService = inject(MessageService);
  private readonly destroyRef     = inject(DestroyRef);
  private readonly cdr            = inject(ChangeDetectorRef);

  @Input() workInstructionId = '';
  @Input() revisionNumber?: number;
  @Input() editable = false;
  @Output() changed = new EventEmitter<void>();

  rows: SkillRequirementDto[] = [];
  skillOptions: SkillOption[] = [];
  selectedSkillId: string | null = null;
  adding = false;

  employeeId = '';
  checking = false;
  qualification: QualificationResultDto | null = null;

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['workInstructionId'] || changes['revisionNumber']) && this.workInstructionId) {
      this.load();
    }
    if (changes['editable'] && this.editable && this.skillOptions.length === 0) {
      this.loadSkills();
    }
  }

  load(): void {
    this.qualification = null;
    this.api.listSkillRequirements(this.workInstructionId, this.revisionNumber)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: reqs => { this.rows = reqs; this.cdr.detectChanges(); },
        error: () => { this.rows = []; this.cdr.detectChanges(); },
      });
  }

  private loadSkills(): void {
    this.listLabourSkills().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: page => {
        this.skillOptions = page.content.map(s => ({
          label: `${s.skillCode} — ${s.name}`, value: s.id,
        }));
        this.cdr.detectChanges();
      },
      error: () => { this.skillOptions = []; this.cdr.detectChanges(); },
    });
  }

  private listLabourSkills(): Observable<LabourSkillsPage> {
    const params = new HttpParams().set('size', 200).set('active', true);
    return this.http.get<LabourSkillsPage>('/api/v1/labour/skills', { params });
  }

  add(): void {
    if (!this.selectedSkillId) return;
    this.adding = true;
    this.api.addSkillRequirement(this.workInstructionId, this.selectedSkillId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.adding = false;
          this.selectedSkillId = null;
          this.messageService.add({ severity: 'success', summary: 'Skill requirement added' });
          this.load();
          this.changed.emit();
          this.cdr.detectChanges();
        },
        error: err => {
          this.adding = false;
          this.messageService.add({ severity: 'error', summary: this.extractError(err) });
          this.cdr.detectChanges();
        },
      });
  }

  remove(req: SkillRequirementDto): void {
    this.api.removeSkillRequirement(this.workInstructionId, req.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.messageService.add({ severity: 'success', summary: 'Skill requirement removed' });
          this.load();
          this.changed.emit();
          this.cdr.detectChanges();
        },
        error: err => {
          this.messageService.add({ severity: 'error', summary: this.extractError(err) });
          this.cdr.detectChanges();
        },
      });
  }

  checkQualification(): void {
    if (!this.employeeId) return;
    this.checking = true;
    this.api.evaluateQualification(this.workInstructionId, { employeeId: this.employeeId },
      this.revisionNumber)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => { this.checking = false; this.qualification = result; this.cdr.detectChanges(); },
        error: err => {
          this.checking = false;
          this.messageService.add({ severity: 'error', summary: this.extractError(err) });
          this.cdr.detectChanges();
        },
      });
  }

  qualLabel(): string {
    if (!this.qualification) return '';
    return this.qualification.qualified ? 'Qualified' : 'Not qualified';
  }

  qualSeverity(): 'success' | 'danger' {
    return this.qualification?.qualified ? 'success' : 'danger';
  }

  private extractError(err: unknown): string {
    const e = err as { error?: { error?: string; details?: string[] } };
    if (e?.error?.details?.length) return e.error.details.join('; ');
    return e?.error?.error ?? 'Operation failed';
  }
}
