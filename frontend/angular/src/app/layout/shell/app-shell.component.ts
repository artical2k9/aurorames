import { Component, inject, OnInit, ViewChild } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';
import { ButtonModule } from 'primeng/button';
import { PopoverModule } from 'primeng/popover';
import { Popover } from 'primeng/popover';
import {
  LucideLayoutDashboard,
  LucidePackage,
  LucideListTree,
  LucidePencilRuler,
  LucideBlocks,
  LucideSettings,
  LucideLifeBuoy,
  LucidePanelLeftOpen,
  LucidePanelLeftClose,
  LucideUserCog,
  LucideList,
  LucideLayoutGrid,
  LucideBell,
} from '@lucide/angular';
import { ThemeToggleComponent } from '../../shared/theme';
import { ThemeService } from '../../shared/theme';
import { BreadcrumbComponent, BreadcrumbService } from '../../shared/ui';

const NAV_COLLAPSED_KEY = 'aurora-mes-nav-collapsed';

type NavIconKey = 'dashboard' | 'item-master' | 'bom' | 'eco' | 'work-orders' | 'settings' | 'help';

interface NavItem {
  label: string;
  iconKey: NavIconKey;
  path: string;
  disabled?: boolean;
}

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive,
    ButtonModule, PopoverModule, BreadcrumbComponent,
    LucideLayoutDashboard, LucidePackage, LucideListTree,
    LucidePencilRuler, LucideBlocks, LucideSettings, LucideLifeBuoy,
    LucidePanelLeftOpen, LucidePanelLeftClose,
    LucideUserCog, LucideList, LucideLayoutGrid, LucideBell,
    ThemeToggleComponent,
  ],
  template: `
    <div class="shell" [class.shell--collapsed]="collapsed">

      <!-- ── Top bar ─────────────────────────────────────────── -->
      <header class="shell__topbar">
        <div class="shell__topbar-start">
          <div class="shell__logo" aria-hidden="true">A</div>
          <app-breadcrumb class="shell__breadcrumb" [crumbs]="breadcrumbSvc.crumbs()" />
        </div>
        <div class="shell__topbar-end">
          <button class="shell__icon-btn shell__notif-btn"
                  [attr.aria-label]="notificationCount > 0 ? notificationCount + ' unread notifications' : 'Notifications'"
                  title="Notifications">
            <svg lucideBell [size]="18" [strokeWidth]="2"></svg>
            @if (notificationCount > 0) {
              <span class="shell__notif-badge" aria-hidden="true"></span>
            }
          </button>
          <div class="shell__view-toggle" role="group" aria-label="View mode">
            <button class="shell__icon-btn"
                    [class.shell__icon-btn--active]="viewMode === 'list'"
                    aria-label="List view" title="List view"
                    (click)="viewMode = 'list'">
              <svg lucideList [size]="16" [strokeWidth]="2"></svg>
            </button>
            <button class="shell__icon-btn"
                    [class.shell__icon-btn--active]="viewMode === 'grid'"
                    aria-label="Grid view" title="Grid view"
                    (click)="viewMode = 'grid'">
              <svg lucideLayoutGrid [size]="16" [strokeWidth]="2"></svg>
            </button>
          </div>
          <app-theme-toggle />
          <button class="shell__icon-btn shell__user-btn"
                  [attr.aria-label]="'User menu for ' + userName"
                  title="User menu"
                  (click)="avatarMenu.toggle($event)">
            <svg lucideUserCog [size]="18" [strokeWidth]="2"></svg>
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
          @if (collapsed) {
            <svg lucidePanelLeftOpen [size]="16" [strokeWidth]="2"></svg>
          } @else {
            <svg lucidePanelLeftClose [size]="16" [strokeWidth]="2"></svg>
          }
        </button>

        <nav class="shell__nav" aria-label="Main navigation">
          @for (item of navItems; track item.path) {
            @if (item.disabled) {
              <span class="shell__nav-item shell__nav-item--disabled" [title]="item.label">
                <span class="shell__nav-icon">
                  @switch (item.iconKey) {
                    @case ('dashboard')   { <svg lucideLayoutDashboard [size]="18" [strokeWidth]="2"></svg> }
                    @case ('item-master') { <svg lucidePackage         [size]="18" [strokeWidth]="2"></svg> }
                    @case ('bom')         { <svg lucideListTree        [size]="18" [strokeWidth]="2"></svg> }
                    @case ('eco')         { <svg lucidePencilRuler     [size]="18" [strokeWidth]="2"></svg> }
                    @case ('work-orders') { <svg lucideBlocks          [size]="18" [strokeWidth]="2"></svg> }
                    @case ('settings')    { <svg lucideSettings        [size]="18" [strokeWidth]="2"></svg> }
                    @case ('help')        { <svg lucideLifeBuoy        [size]="18" [strokeWidth]="2"></svg> }
                  }
                </span>
                @if (!collapsed) {
                  <span class="shell__nav-label">{{ item.label }}</span>
                }
              </span>
            } @else {
              <a class="shell__nav-item"
                 [routerLink]="item.path"
                 routerLinkActive="shell__nav-item--active"
                 [routerLinkActiveOptions]="{ exact: false }"
                 [title]="item.label">
                <span class="shell__nav-icon">
                  @switch (item.iconKey) {
                    @case ('dashboard')   { <svg lucideLayoutDashboard [size]="18" [strokeWidth]="2"></svg> }
                    @case ('item-master') { <svg lucidePackage         [size]="18" [strokeWidth]="2"></svg> }
                    @case ('bom')         { <svg lucideListTree        [size]="18" [strokeWidth]="2"></svg> }
                    @case ('eco')         { <svg lucidePencilRuler     [size]="18" [strokeWidth]="2"></svg> }
                    @case ('work-orders') { <svg lucideBlocks          [size]="18" [strokeWidth]="2"></svg> }
                    @case ('settings')    { <svg lucideSettings        [size]="18" [strokeWidth]="2"></svg> }
                    @case ('help')        { <svg lucideLifeBuoy        [size]="18" [strokeWidth]="2"></svg> }
                  }
                </span>
                @if (!collapsed) {
                  <span class="shell__nav-label">{{ item.label }}</span>
                }
              </a>
            }
          }
        </nav>

        <nav class="shell__nav shell__nav--bottom" aria-label="Settings and help">
          @for (item of navItemsBottom; track item.label) {
            <span class="shell__nav-item shell__nav-item--disabled" [title]="item.label">
              <span class="shell__nav-icon">
                @switch (item.iconKey) {
                  @case ('dashboard')   { <svg lucideLayoutDashboard [size]="18" [strokeWidth]="2"></svg> }
                  @case ('item-master') { <svg lucidePackage         [size]="18" [strokeWidth]="2"></svg> }
                  @case ('bom')         { <svg lucideListTree        [size]="18" [strokeWidth]="2"></svg> }
                  @case ('eco')         { <svg lucidePencilRuler     [size]="18" [strokeWidth]="2"></svg> }
                  @case ('work-orders') { <svg lucideBlocks          [size]="18" [strokeWidth]="2"></svg> }
                  @case ('settings')    { <svg lucideSettings        [size]="18" [strokeWidth]="2"></svg> }
                  @case ('help')        { <svg lucideLifeBuoy        [size]="18" [strokeWidth]="2"></svg> }
                }
              </span>
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
export class AppShellComponent implements OnInit {
  @ViewChild('avatarMenu') avatarMenu!: Popover;

  private readonly oauthService = inject(OAuthService);
  readonly theme = inject(ThemeService);
  readonly breadcrumbSvc = inject(BreadcrumbService);

  collapsed = true;
  viewMode: 'grid' | 'list' = 'list';
  notificationCount = 0;

  readonly navItems: NavItem[] = [
    { label: 'Dashboard',   iconKey: 'dashboard',   path: '/dashboard' },
    { label: 'Item Master', iconKey: 'item-master', path: '/item-master' },
    { label: 'BOM',         iconKey: 'bom',         path: '/bom' },
    { label: 'ECO',         iconKey: 'eco',         path: '/ecos' },
    { label: 'Work Orders', iconKey: 'work-orders', path: '/work-orders', disabled: true },
  ];

  readonly navItemsBottom: NavItem[] = [
    { label: 'Settings', iconKey: 'settings', path: '/settings' },
    { label: 'Help',     iconKey: 'help',     path: '/help' },
  ];

  ngOnInit(): void {
    const saved = localStorage.getItem(NAV_COLLAPSED_KEY);
    this.collapsed = saved !== null ? saved === 'true' : true;
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
