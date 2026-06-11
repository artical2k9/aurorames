import { inject } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { OAuthService } from 'angular-oauth2-oidc';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthSessionService } from './auth-session.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const oauth       = inject(OAuthService);
  const authSession = inject(AuthSessionService);

  const token   = oauth.getAccessToken();
  const authReq = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      // Only intercept 401s that are not from Keycloak's own endpoints.
      if (error.status !== 401 || req.url.includes('/realms/')) {
        return throwError(() => error);
      }

      // Pre-auth endpoints (e.g. change-temporary-password) can return 401 if
      // the backend is misconfigured — there is no refresh token to use, so
      // propagate the error to the caller instead of triggering endSession().
      if (!oauth.getRefreshToken()) {
        return throwError(() => error);
      }

      // Attempt one silent refresh then replay the original request.
      // refreshOnce() is shared — concurrent 401s from multiple in-flight
      // requests all subscribe to the same observable and hit the token
      // endpoint exactly once.
      // The returned token comes directly from the TokenResponse so we never
      // race against the library's internal storage write.
      return authSession.refreshOnce().pipe(
        switchMap((newToken) => {
          const retryReq = newToken
            ? req.clone({ setHeaders: { Authorization: `Bearer ${newToken}` } })
            : req;
          return next(retryReq);
        }),
        catchError(() => {
          authSession.endSession();
          return throwError(() => error);
        }),
      );
    }),
  );
};
