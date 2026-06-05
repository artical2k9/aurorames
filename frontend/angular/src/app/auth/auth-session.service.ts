import { Injectable, NgZone, inject } from '@angular/core';
import { Router } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';
import {
  Observable, catchError, finalize, from, map, shareReplay, throwError,
} from 'rxjs';
import { environment } from '../../environments/environment';

const SESSION_START_KEY = 'mes_session_start';
// Timestamp written by whichever tab last initiated a refresh.
// Other tabs check this before starting their own refresh to avoid
// invalidating a rotating refresh token that was just rotated.
const REFRESH_LOCK_KEY  = 'mes_refresh_lock';
const REFRESH_LOCK_TTL  = 10_000; // ms — skip if another tab refreshed within this window

@Injectable({ providedIn: 'root' })
export class AuthSessionService {
  private readonly oauth  = inject(OAuthService);
  private readonly router = inject(Router);
  private readonly zone   = inject(NgZone);

  private refreshTimer?: ReturnType<typeof setTimeout>;
  // Shared observable: multiple callers (e.g. concurrent 401s) share one refresh call.
  private refreshOnce$: Observable<void> | null = null;

  constructor() {
    // Cross-tab coordination: when any tab stores a new access_token in localStorage
    // the 'storage' event fires in every OTHER tab.  Reschedule from the new expiry
    // so we don't attempt a second refresh with an already-rotated refresh token.
    window.addEventListener('storage', (event) => {
      if (event.key === 'access_token' && event.newValue) {
        this.zone.run(() => this.scheduleRefresh());
      }
    });
  }

  /** Called by login component immediately after a successful password-grant. */
  startSession(): void {
    localStorage.setItem(SESSION_START_KEY, String(Date.now()));
    this.scheduleRefresh();
  }

  /**
   * Called by APP_INITIALIZER on every page load.
   * If a valid session exists in localStorage, restarts the refresh cycle so a
   * page refresh does not force the user to re-authenticate.
   */
  restoreSession(): void {
    if (!this.isSessionValid()) {
      return;
    }
    if (!this.oauth.hasValidAccessToken() && this.oauth.getRefreshToken()) {
      // Access token expired during the page-reload gap — refresh immediately.
      this.refreshOnce().subscribe({
        next:  () => this.scheduleRefresh(),
        error: () => this.endSession(),
      });
    } else {
      this.scheduleRefresh();
    }
  }

  /**
   * True when:
   *  - a session start timestamp is recorded in localStorage,
   *  - it has not exceeded the configured session timeout, AND
   *  - either a valid access token or a refresh token is available.
   */
  isSessionValid(): boolean {
    const raw = localStorage.getItem(SESSION_START_KEY);
    if (!raw) return false;
    if (Date.now() - parseInt(raw, 10) > environment.sessionTimeoutMs) return false;
    return this.oauth.hasValidAccessToken() || !!this.oauth.getRefreshToken();
  }

  /** Clears session state, revokes local tokens and navigates to /login. */
  endSession(): void {
    this.clearTimer();
    localStorage.removeItem(SESSION_START_KEY);
    this.oauth.logOut(true);
    this.router.navigateByUrl('/login');
  }

  /**
   * Performs a single token refresh.  If multiple callers request a refresh
   * simultaneously (e.g. several concurrent API calls all receive 401) they all
   * receive the same observable and the network call is made exactly once.
   */
  refreshOnce(): Observable<void> {
    if (!this.refreshOnce$) {
      localStorage.setItem(REFRESH_LOCK_KEY, String(Date.now()));

      this.refreshOnce$ = from(this.oauth.refreshToken()).pipe(
        map(() => void 0),
        catchError((err) => throwError(() => err)),
        finalize(() => { this.refreshOnce$ = null; }),
        shareReplay(1),
      );
    }
    return this.refreshOnce$;
  }

  // ── private ────────────────────────────────────────────────────────────────

  private scheduleRefresh(): void {
    this.clearTimer();

    const expiry = this.oauth.getAccessTokenExpiration();
    if (!expiry) return;

    const delay = Math.max(0, expiry - Date.now() - environment.tokenRefreshMarginMs);
    this.refreshTimer = setTimeout(() => this.zone.run(() => this.doRefresh()), delay);
  }

  private doRefresh(): void {
    if (!this.isSessionValid()) {
      this.endSession();
      return;
    }

    // Cross-tab check: if another tab refreshed very recently its lock entry will
    // still be within the TTL.  Skip our refresh and reschedule so we pick up the
    // new expiry that the winning tab just wrote to localStorage.
    const lock = localStorage.getItem(REFRESH_LOCK_KEY);
    if (lock && Date.now() - parseInt(lock, 10) < REFRESH_LOCK_TTL) {
      this.scheduleRefresh();
      return;
    }

    this.refreshOnce().subscribe({
      next:  () => this.scheduleRefresh(),
      error: () => this.endSession(),
    });
  }

  private clearTimer(): void {
    if (this.refreshTimer !== undefined) {
      clearTimeout(this.refreshTimer);
      this.refreshTimer = undefined;
    }
  }
}
