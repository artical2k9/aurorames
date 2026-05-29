import { Component, inject } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { ThemeService } from '../../services/theme.service';

@Component({
  selector: 'app-theme-toggle',
  standalone: true,
  imports: [AsyncPipe, ButtonModule],
  template: `
    @if (theme.isDark$ | async) {
      <p-button
        icon="pi pi-sun"
        [rounded]="true"
        [text]="true"
        aria-label="Switch to light mode"
        (onClick)="theme.toggle()"
        styleClass="theme-toggle"
      />
    } @else {
      <p-button
        icon="pi pi-moon"
        [rounded]="true"
        [text]="true"
        aria-label="Switch to dark mode"
        (onClick)="theme.toggle()"
        styleClass="theme-toggle"
      />
    }
  `,
  styles: [`
    :host ::ng-deep .theme-toggle { width: 36px; height: 36px; }
  `],
})
export class ThemeToggleComponent {
  readonly theme = inject(ThemeService);
}
