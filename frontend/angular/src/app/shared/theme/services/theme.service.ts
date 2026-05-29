import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

const STORAGE_KEY = 'aurora-mes-theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly isDark$ = new BehaviorSubject<boolean>(false);

  init(): void {
    const saved = localStorage.getItem(STORAGE_KEY);
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    const dark = saved !== null ? saved === 'dark' : prefersDark;
    this.isDark$.next(dark);
    this.applyClass(dark);
  }

  toggle(): void {
    const next = !this.isDark$.value;
    this.isDark$.next(next);
    localStorage.setItem(STORAGE_KEY, next ? 'dark' : 'light');
    this.applyClass(next);
  }

  private applyClass(dark: boolean): void {
    if (dark) {
      document.documentElement.classList.add('aurora-dark');
    } else {
      document.documentElement.classList.remove('aurora-dark');
    }
  }
}
