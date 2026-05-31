import { Component, inject, OnInit, ViewChild } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';
import { ButtonModule } from 'primeng/button';
import { PopoverModule } from 'primeng/popover';
import { Popover } from 'primeng/popover';
import { ThemeToggleComponent } from '../../shared/theme';
import { ThemeService } from '../../shared/theme';

const NAV_COLLAPSED_KEY = 'aurora-mes-nav-collapsed';

interface NavItem {
  label: string;
  icon: string;
  path: string;
}

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive,
    ButtonModule, PopoverModule,
    ThemeToggleComponent,
  ],
  template: `
    <div class="shell" [class.shell--collapsed]="collapsed">

      <!-- ── Top bar ─────────────────────────────────────────── -->
      <header class="shell__topbar">
        <div class="shell__topbar-brand">
          @if (!collapsed) {
            <span class="shell__wordmark">Aurora MES</span>
          } @else {
            <span class="shell__wordmark-icon">A</span>
          }
        </div>
        <div class="shell__topbar-actions">
          <app-theme-toggle />
          <button class="shell__avatar"
                  [attr.aria-label]="'User menu for ' + userName"
                  (click)="avatarMenu.toggle($event)">
            {{ userInitial }}
          </button>
        </div>
      </header>

      <!-- ── Avatar popover ──────────────────────────────────── -->
      <p-popover #avatarMenu>
        <div class="shell__user-menu">
          <div class="shell__user-name">{{ userName }}</div>
          <div class="shell__user-email">{{ userEmail }}</div>
          <hr class="shell__user-divider" />
          <button class="shell__user-logout" (click)="logout(); avatarMenu.hide()">
            Logout
          </button>
        </div>
      </p-popover>

      <!-- ── Nav rail ────────────────────────────────────────── -->
      <aside class="shell__rail">
        <button class="shell__collapse-btn"
                [attr.aria-label]="collapsed ? 'Expand navigation' : 'Collapse navigation'"
                (click)="toggleCollapse()">
          <i [class]="collapsed ? 'pi pi-angle-right' : 'pi pi-angle-left'"></i>
        </button>

        <nav class="shell__nav">
          @for (item of navItems; track item.path) {
            <a class="shell__nav-item"
               [routerLink]="item.path"
               routerLinkActive="shell__nav-item--active"
               [attr.aria-label]="item.label"
               [title]="collapsed ? item.label : ''">
              <i [class]="'pi ' + item.icon + ' shell__nav-icon'"></i>
              @if (!collapsed) {
                <span class="shell__nav-label">{{ item.label }}</span>
              }
            </a>
          }
        </nav>
      </aside>

      <!-- ── Content area ────────────────────────────────────── -->
      <main class="shell__content">
        <router-outlet />
      </main>

    </div>
  `,
  styleUrl: './app-shell.component.scss',
})
export class AppShellComponent implements OnInit {
  @ViewChild('avatarMenu') avatarMenu!: Popover;

  private readonly oauthService = inject(OAuthService);
  readonly theme = inject(ThemeService);

  collapsed = false;

  readonly navItems: NavItem[] = [
    { label: 'Dashboard',    icon: 'pi-th-large',  path: '/dashboard' },
    { label: 'Item Master',  icon: 'pi-database',  path: '/item-master' },
    { label: 'BOM',          icon: 'pi-sitemap',   path: '/bom' },
    { label: 'ECO',          icon: 'pi-file-edit', path: '/ecos' },
  ];

  ngOnInit(): void {
    const saved = localStorage.getItem(NAV_COLLAPSED_KEY);
    this.collapsed = saved === 'true';
  }

  toggleCollapse(): void {
    this.collapsed = !this.collapsed;
    localStorage.setItem(NAV_COLLAPSED_KEY, String(this.collapsed));
  }

  get userClaims(): Record<string, unknown> {
    return (this.oauthService.getIdentityClaims() as Record<string, unknown>) ?? {};
  }

  get userName(): string {
    return (this.userClaims['preferred_username'] as string)
      || (this.userClaims['name'] as string)
      || 'User';
  }

  get userEmail(): string {
    return (this.userClaims['email'] as string) || '';
  }

  get userInitial(): string {
    return this.userName.charAt(0).toUpperCase();
  }

  logout(): void {
    this.oauthService.logOut();
  }
}
