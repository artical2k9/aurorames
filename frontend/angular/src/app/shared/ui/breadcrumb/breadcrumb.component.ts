import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-breadcrumb',
  standalone: true,
  imports: [RouterLink],
  template: `
    <nav class="breadcrumb" aria-label="Breadcrumb">
      @for (crumb of crumbs; track crumb.label; let last = $last; let i = $index) {
        @if (i > 0) {
          <span class="breadcrumb__sep" aria-hidden="true">/</span>
        }
        @if (last || !crumb.route) {
          <span [class]="last ? 'breadcrumb__current' : 'breadcrumb__segment'">{{ crumb.label }}</span>
        } @else {
          <a [routerLink]="crumb.route" class="breadcrumb__link">{{ crumb.label }}</a>
        }
      }
    </nav>
  `,
  styles: [`
    .breadcrumb {
      display: flex; align-items: center; gap: 0.375rem; flex-wrap: wrap;
      font-size: 0.8125rem; margin-bottom: 1rem;
      color: var(--p-text-muted-color);
    }
    .breadcrumb__link {
      color: var(--p-primary-color); text-decoration: none;
    }
    .breadcrumb__link:hover { text-decoration: underline; }
    .breadcrumb__sep { color: var(--p-text-muted-color); }
    .breadcrumb__segment { color: var(--p-text-muted-color); }
    .breadcrumb__current { font-weight: 500; color: var(--p-text-color); }
  `],
})
export class BreadcrumbComponent {
  @Input() crumbs: { label: string; route?: string[] }[] = [];
}
