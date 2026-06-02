import { Component, inject } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { LucideSunrise, LucideSunset } from '@lucide/angular';
import { ThemeService } from '../../services/theme.service';

@Component({
  selector: 'app-theme-toggle',
  standalone: true,
  imports: [AsyncPipe, LucideSunrise, LucideSunset],
  template: `
    @if (theme.isDark$ | async) {
      <button (click)="theme.toggle()" aria-label="Switch to light mode" title="Switch to light mode">
        <svg lucideSunrise [size]="18" [strokeWidth]="2"></svg>
      </button>
    } @else {
      <button (click)="theme.toggle()" aria-label="Switch to dark mode" title="Switch to dark mode">
        <svg lucideSunset [size]="18" [strokeWidth]="2"></svg>
      </button>
    }
  `,
  styles: [`
    button {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 34px;
      height: 34px;
      border: none;
      border-radius: 6px;
      background: transparent;
      cursor: pointer;
      color: var(--aurora-text-secondary);
      transition: background 120ms ease, color 120ms ease;
      padding: 0;
    }
    button:hover {
      background: color-mix(in srgb, var(--aurora-brand-primary) 10%, transparent);
      color: var(--aurora-brand-primary);
    }
  `],
})
export class ThemeToggleComponent {
  readonly theme = inject(ThemeService);
}
