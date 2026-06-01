import { Component, inject, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router, NavigationEnd } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';
import { ButtonModule } from 'primeng/button';
import { PopoverModule } from 'primeng/popover';
import { Popover } from 'primeng/popover';
import { ThemeToggleComponent } from '../../shared/theme';
import { ThemeService } from '../../shared/theme';
import { filter, Subscription } from 'rxjs';

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
        <div class="shell__topbar-start">
          <div class="shell__logo" aria-hidden="true">A</div>
          <nav class="shell__breadcrumb" aria-label="breadcrumb">
            <span class="shell__breadcrumb-text">{{ currentPageLabel }}</span>
          </nav>
        </div>
        <div class="shell__topbar-end">
          <button class="shell__icon-btn" aria-label="Notifications" title="Notifications">
            <i class="pi pi-bell"></i>
          </button>
          <div class="shell__view-toggle" role="group" aria-label="View mode">
            <button class="shell__icon-btn"
                    [class.shell__icon-btn--active]="viewMode === 'list'"
                    aria-label="List view"
                    title="List view"
                    (click)="viewMode = 'list'">
              <i class="pi pi-list"></i>
            </button>
            <button class="shell__icon-btn"
                    [class.shell__icon-btn--active]="viewMode === 'grid'"
                    aria-label="Grid view"
                    title="Grid view"
                    (click)="viewMode = 'grid'">
              <i class="pi pi-th-large"></i>
            </button>
          </div>
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

        <nav class="shell__nav" aria-label="Main navigation">
          @for (item of navItems; track item.path) {
            <a class="shell__nav-item"
               [routerLink]="item.path"
               routerLinkActive="shell__nav-item--active"
               [routerLinkActiveOptions]="{ exact: false }"
               [title]="item.label">
              <i [class]="'pi ' + item.icon + ' shell__nav-icon'"></i>
              @if (!collapsed) {
                <span class="shell__nav-label">{{ item.label }}</span>
              }
            </a>
          }
        </nav>

        <nav class="shell__nav shell__nav--bottom" aria-label="Settings and help">
          @for (item of navItemsBottom; track item.label) {
            <span class="shell__nav-item shell__nav-item--disabled"
                  [title]="item.label">
              <i [class]="'pi ' + item.icon + ' shell__nav-icon'"></i>
              @if (!collapsed) {
                <span class="shell__nav-label">{{ item.label }}</span>
              }
            </span>
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
export class AppShellComponent implements OnInit, OnDestroy {
  @ViewChild('avatarMenu') avatarMenu!: Popover;

  private readonly oauthService = inject(OAuthService);
  private readonly router = inject(Router);
  readonly theme = inject(ThemeService);

  collapsed = true;
  viewMode: 'grid' | 'list' = 'list';
  currentPageLabel = 'Dashboard';

  private routerSub?: Subscription;

  readonly navItems: NavItem[] = [
    { label: 'Dashboard',   icon: 'pi-home',        path: '/dashboard' },
    { label: 'Item Master', icon: 'pi-box',         path: '/item-master' },
    { label: 'BOM',         icon: 'pi-sitemap',     path: '/bom' },
    { label: 'ECO',         icon: 'pi-file-check',  path: '/ecos' },
  ];

  readonly navItemsBottom: NavItem[] = [
    { label: 'Settings', icon: 'pi-cog',             path: '/settings' },
    { label: 'Help',     icon: 'pi-question-circle', path: '/help' },
  ];

  private readonly allNavItems: NavItem[] = [...this.navItems, ...this.navItemsBottom];

  ngOnInit(): void {
    const saved = localStorage.getItem(NAV_COLLAPSED_KEY);
    this.collapsed = saved !== null ? saved === 'true' : true;

    this.updatePageLabel(this.router.url);
    this.routerSub = this.router.events
      .pipe(filter(e => e instanceof NavigationEnd))
      .subscribe(e => this.updatePageLabel((e as NavigationEnd).urlAfterRedirects));
  }

  ngOnDestroy(): void {
    this.routerSub?.unsubscribe();
  }

  private updatePageLabel(url: string): void {
    const segment = '/' + url.split('/').filter(Boolean)[0];
    const match = this.allNavItems.find(i => i.path === segment);
    this.currentPageLabel = match?.label ?? 'Aurora MES';
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
