import { ApplicationConfig, APP_INITIALIZER, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideOAuthClient, OAuthService } from 'angular-oauth2-oidc';

import { routes } from './app.routes';
import { authConfig } from './auth/auth.config';
import { authInterceptor } from './auth/auth.interceptor';

function initializeOAuth(oauthService: OAuthService) {
  return () => {
    oauthService.configure(authConfig);
    return oauthService.loadDiscoveryDocumentAndTryLogin();
  };
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideOAuthClient(),
    {
      provide: APP_INITIALIZER,
      useFactory: initializeOAuth,
      deps: [OAuthService],
      multi: true,
    },
  ]
};
