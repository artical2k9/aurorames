import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CdkDragDrop, CdkDropList, CdkDrag, moveItemInArray } from '@angular/cdk/drag-drop';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { TagModule } from 'primeng/tag';
import { ColumnDef } from '../../models/column-def.model';

@Component({
  selector: 'app-column-picker',
  standalone: true,
  imports: [CommonModule, FormsModule, CdkDropList, CdkDrag, ButtonModule, CheckboxModule, TagModule],
  template: `
    <div class="column-picker">
      <div class="column-picker__header">
        <span>Columns</span>
        <a class="column-picker__reset" (click)="reset.emit()">Reset to default</a>
      </div>

      <div class="column-picker__section-label">Standard Columns</div>
      <div cdkDropList [cdkDropListData]="standardColumns" (cdkDropListDropped)="drop($event, false)">
        @for (col of standardColumns; track col.key) {
          <div class="column-picker__row" cdkDrag [cdkDragDisabled]="col.locked ?? false">
            <span *ngIf="!col.locked" class="column-picker__drag-handle" cdkDragHandle>⠿</span>
            <p-checkbox [(ngModel)]="col.visible" [binary]="true" [disabled]="col.locked ?? false" />
            <span class="column-picker__label">{{ col.label }}</span>
            @if (col.locked) {
              <p-tag value="Required" severity="secondary" />
            }
          </div>
        }
      </div>

      @if (udfColumns.length > 0) {
        <div class="column-picker__section-label">User-Defined Fields</div>
        <div cdkDropList [cdkDropListData]="udfColumns" (cdkDropListDropped)="drop($event, true)">
          @for (col of udfColumns; track col.key) {
            <div class="column-picker__row" cdkDrag>
              <span class="column-picker__drag-handle" cdkDragHandle>⠿</span>
              <p-checkbox [(ngModel)]="col.visible" [binary]="true" />
              <span class="column-picker__label">{{ col.label }}</span>
              <p-tag value="UDF" styleClass="column-picker__udf-tag" />
            </div>
          }
        </div>
      }

      <div class="column-picker__footer">
        <p-button label="Apply" size="small" (onClick)="onApply()" />
        <p-button label="Cancel" size="small" severity="secondary" (onClick)="applied.emit(null!)" />
      </div>
    </div>
  `,
  styles: [`
    .column-picker { min-width: 280px; padding: 0.75rem; }
    .column-picker__header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.5rem; font-weight: 600; }
    .column-picker__reset { cursor: pointer; font-size: 0.8rem; color: var(--p-primary-color); }
    .column-picker__section-label { font-size: 0.75rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; color: var(--p-text-muted-color); margin: 0.5rem 0 0.25rem; }
    .column-picker__row { display: flex; align-items: center; gap: 0.5rem; padding: 0.25rem 0; }
    .column-picker__drag-handle { cursor: grab; color: var(--p-text-muted-color); }
    .column-picker__label { flex: 1; }
    .column-picker__footer { display: flex; gap: 0.5rem; justify-content: flex-end; margin-top: 0.75rem; padding-top: 0.75rem; border-top: 1px solid var(--p-surface-border); }
    :host ::ng-deep .column-picker__udf-tag .p-tag { background: #BFDBFE; color: #1E40AF; }
  `],
})
export class ColumnPickerComponent implements OnChanges {
  @Input() columns: ColumnDef[] = [];
  @Output() applied = new EventEmitter<ColumnDef[]>();
  @Output() reset = new EventEmitter<void>();

  standardColumns: ColumnDef[] = [];
  udfColumns: ColumnDef[] = [];

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['columns']) {
      this.standardColumns = this.columns.filter(c => !c.udf).map(c => ({ ...c }));
      this.udfColumns = this.columns.filter(c => c.udf).map(c => ({ ...c }));
    }
  }

  drop(event: CdkDragDrop<ColumnDef[]>, isUdf: boolean): void {
    const list = isUdf ? this.udfColumns : this.standardColumns;
    moveItemInArray(list, event.previousIndex, event.currentIndex);
    list.forEach((c, i) => (c.order = i));
  }

  onApply(): void {
    const all = [...this.standardColumns, ...this.udfColumns].map((c, i) => ({ ...c, order: i }));
    this.applied.emit(all);
  }
}
