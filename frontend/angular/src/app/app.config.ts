import { ApplicationConfig, APP_INITIALIZER, provideBrowserGlobalErrorListeners } from '@angular/core';
import {
  ActivatedRouteSnapshot,
  BaseRouteReuseStrategy,
  provideRouter,
  RouteReuseStrategy,
  withRouterConfig,
} from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideOAuthClient, OAuthService } from 'angular-oauth2-oidc';
import { providePrimeNG } from 'primeng/config';
import { definePreset } from '@primeng/themes';
import Aura from '@primeng/themes/aura';

import { routes } from './app.routes';
import { authConfig } from './auth/auth.config';
import { authInterceptor } from './auth/auth.interceptor';

const AuroraPreset = definePreset(Aura, {
  semantic: {
    colorScheme: {
      dark: {
        primary: {
          color: '#1A5FD4',
          contrastColor: '#ffffff',
          hoverColor: '#2E8BF5',
          activeColor: '#6BB8FF',
        },
        text: {
          color: '#E8EDF5',
          hoverColor: '#F0F4FA',
          mutedColor: '#8A9BB0',
          hoverMutedColor: '#C5CEDC',
        },
      },
    },
  },
});

// Leaf-level route components (pages) are never reused so that:
// - Same-URL nav clicks (onSameUrlNavigation: 'reload') actually recreate the
//   component and re-run ngOnInit / ngAfterViewInit, triggering a fresh data load.
// - takeUntilDestroyed correctly cancels in-flight requests when navigating away.
// Parent routes (AppShellComponent — has firstChild) are still reused so the
// shell/nav-rail does not flicker on every navigation.
class LeafComponentReloadStrategy extends BaseRouteReuseStrategy {
  override shouldReuseRoute(
    future: ActivatedRouteSnapshot,
    curr: ActivatedRouteSnapshot,
  ): boolean {
    if (!future.firstChild) {
      return false;
    }
    return super.shouldReuseRoute(future, curr);
  }
}

function initializeOAuth(oauthService: OAuthService) {
  return () => {
    oauthService.configure(authConfig);
    // loadDiscoveryDocument only — the app uses the password-grant flow, so
    // there is no PKCE redirect callback to complete.  Calling tryLogin() here
    // would attempt id_token nonce validation without a stored nonce, causing
    // a silent "invalid_nonce_in_state" rejection that makes getAccessToken()
    // return null on every subsequent request.
    return oauthService.loadDiscoveryDocument().catch(() => false);
  };
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideAnimationsAsync(),
    provideRouter(routes, withRouterConfig({ onSameUrlNavigation: 'reload' })),
    { provide: RouteReuseStrategy, useClass: LeafComponentReloadStrategy },
    provideHttpClient(withInterceptors([authInterceptor])),
    provideOAuthClient(),
    providePrimeNG({
      theme: { preset: AuroraPreset, options: { darkModeSelector: '.aurora-dark' } },
    }),
    {
      provide: APP_INITIALIZER,
      useFactory: initializeOAuth,
      deps: [OAuthService],
      multi: true,
    },
  ]
};
