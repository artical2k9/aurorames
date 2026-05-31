import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-status-badge',
  standalone: true,
  template: `
    <span class="status-badge" [class]="'status-badge--' + status.toLowerCase()">
      <span class="status-badge__dot">{{ dot }}</span>
      {{ status }}
    </span>
  `,
  styles: [`
    .status-badge { display: inline-flex; align-items: center; gap: 0.375rem; font-size: 0.8125rem; font-weight: 500; }
    .status-badge__dot { font-size: 0.75rem; }
    .status-badge--active  .status-badge__dot { color: #22C55E; }
    .status-badge--obsolete .status-badge__dot { color: #94A3B8; }
    .status-badge--inactive .status-badge__dot { color: #F59E0B; }
    .status-badge--active  { color: #22C55E; }
    .status-badge--obsolete { color: #94A3B8; }
    .status-badge--inactive { color: #F59E0B; }
  `],
})
export class StatusBadgeComponent {
  @Input({ required: true }) status!: string;

  get dot(): string {
    switch (this.status.toUpperCase()) {
      case 'ACTIVE':   return '●';
      case 'OBSOLETE': return '◎';
      default:         return '●';
    }
  }
}
