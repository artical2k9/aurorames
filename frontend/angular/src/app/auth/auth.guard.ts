import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthSessionService } from './auth-session.service';

export const authGuard: CanActivateFn = () => {
  const authSession = inject(AuthSessionService);
  const router      = inject(Router);

  if (authSession.isSessionValid()) {
    return true;
  }

  const returnUrl = window.location.pathname + window.location.search;
  return router.createUrlTree(['/login'], {
    queryParams: returnUrl && returnUrl !== '/' ? { returnUrl } : {},
  });
};
