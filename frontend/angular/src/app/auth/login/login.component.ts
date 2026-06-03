import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';
import { LucideUser, LucideLock, LucideEye, LucideEyeOff, LucideShieldCheck, LucideLoader } from '@lucide/angular';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, LucideUser, LucideLock, LucideEye, LucideEyeOff, LucideShieldCheck, LucideLoader],
  template: `
    <div class="login-page">

      <div class="login-branding"></div>

      <div class="login-panel">
        <div class="login-card">

          <div class="login-header">
            <span class="login-welcome">Welcome to</span>
            <h1 class="login-title">AURORA MES</h1>
            <p class="login-subtitle">Aerospace-Grade Manufacturing Intelligence</p>
          </div>

          <form class="login-form" (ngSubmit)="signIn()">

            <div class="field-group">
              <label class="field-label" for="username">Username</label>
              <div class="field-wrap">
                <svg lucideUser class="field-icon" [size]="18" [strokeWidth]="1.5"></svg>
                <input id="username" type="text" class="field-input"
                       placeholder="Enter your username"
                       [(ngModel)]="usernameValue" name="username"
                       autocomplete="username" />
              </div>
            </div>

            <div class="field-group">
              <label class="field-label" for="password">Password</label>
              <div class="field-wrap">
                <svg lucideLock class="field-icon" [size]="18" [strokeWidth]="1.5"></svg>
                <input id="password" [type]="showPassword ? 'text' : 'password'" class="field-input"
                       placeholder="Enter your password"
                       [(ngModel)]="passwordValue" name="password"
                       autocomplete="current-password" />
                <button type="button" class="field-eye"
                        [attr.aria-label]="showPassword ? 'Hide password' : 'Show password'"
                        (click)="showPassword = !showPassword">
                  @if (showPassword) {
                    <svg lucideEyeOff [size]="18" [strokeWidth]="1.5"></svg>
                  } @else {
                    <svg lucideEye [size]="18" [strokeWidth]="1.5"></svg>
                  }
                </button>
              </div>
            </div>

            <div class="login-options">
              <label class="remember-label">
                <input type="checkbox" class="remember-check"
                       [(ngModel)]="rememberMe" name="remember" />
                <span>Remember me</span>
              </label>
              <a class="forgot-link" href="#" (click)="$event.preventDefault()">Forgot password?</a>
            </div>

            @if (errorMessage) {
              <div class="login-error" role="alert">{{ errorMessage }}</div>
            }

            <button type="submit" class="btn-primary" [disabled]="isLoading">
              @if (isLoading) {
                <svg lucideLoader class="btn-spinner" [size]="18" [strokeWidth]="2"></svg>
                Signing in…
              } @else {
                Sign In
              }
            </button>

          </form>

          <div class="login-divider">
            <span>or continue with</span>
          </div>

          <button type="button" class="btn-sso" (click)="signInSSO()">
            <svg lucideShieldCheck [size]="18" [strokeWidth]="1.5"></svg>
            SSO / SAML
          </button>

          <p class="login-footer">© 2024 Aurora MES. All rights reserved.</p>

        </div>
      </div>

    </div>
  `,
  styles: [`
    :host { display: block; }

    /* ── Page shell ─────────────────────────────────────────────────────────── */
    .login-page {
      min-height: 100vh;
      background: url('/login-bg.png') center / cover no-repeat;
      display: grid;
      grid-template-columns: 1fr 520px;
    }

    .login-branding { /* left column — background image shows through */ }

    .login-panel {
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 2rem 2.5rem 2rem 1.5rem;
    }

    /* ── Card ───────────────────────────────────────────────────────────────── */
    .login-card {
      width: 100%;
      max-width: 460px;
      background: rgba(6, 14, 32, 0.88);
      backdrop-filter: blur(18px);
      -webkit-backdrop-filter: blur(18px);
      border: 1px solid rgba(37, 99, 235, 0.18);
      border-radius: 16px;
      padding: 2.5rem 2.25rem;
      box-shadow:
        0 0 0 1px rgba(37, 99, 235, 0.06),
        0 24px 64px rgba(0, 0, 0, 0.65),
        0 0 40px rgba(37, 99, 235, 0.06);
    }

    /* ── Header ─────────────────────────────────────────────────────────────── */
    .login-header {
      margin-bottom: 2rem;
    }

    .login-welcome {
      display: block;
      font-size: 0.9375rem;
      font-weight: 500;
      color: #3B82F6;
      letter-spacing: 0.01em;
      margin-bottom: 0.25rem;
    }

    .login-title {
      margin: 0 0 0.5rem;
      font-family: 'Nasalization', sans-serif;
      font-size: 2rem;
      font-weight: normal;
      letter-spacing: 0.08em;
      color: #FFFFFF;
      line-height: 1.1;
    }

    .login-subtitle {
      margin: 0;
      font-size: 0.875rem;
      color: #8A9BB0;
      letter-spacing: 0.01em;
    }

    /* ── Form ───────────────────────────────────────────────────────────────── */
    .login-form {
      display: flex;
      flex-direction: column;
      gap: 1.125rem;
    }

    .field-group {
      display: flex;
      flex-direction: column;
      gap: 0.375rem;
    }

    .field-label {
      font-size: 0.8125rem;
      font-weight: 500;
      color: #C8D4E3;
      letter-spacing: 0.01em;
    }

    .field-wrap {
      position: relative;
      display: flex;
      align-items: center;
    }

    .field-icon {
      position: absolute;
      left: 0.875rem;
      color: #4A6080;
      pointer-events: none;
      flex-shrink: 0;
    }

    .field-input {
      width: 100%;
      background: rgba(10, 22, 48, 0.75);
      border: 1px solid rgba(36, 51, 80, 0.9);
      border-radius: 8px;
      padding: 0.75rem 0.875rem 0.75rem 2.75rem;
      font-size: 0.9rem;
      color: #E8EDF5;
      outline: none;
      transition: border-color 160ms ease, box-shadow 160ms ease;
      box-sizing: border-box;

      &::placeholder { color: #3D5270; }

      &:focus {
        border-color: #2563EB;
        box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.18);
      }
    }

    .field-eye {
      position: absolute;
      right: 0.75rem;
      display: flex;
      align-items: center;
      justify-content: center;
      width: 28px;
      height: 28px;
      background: none;
      border: none;
      cursor: pointer;
      color: #4A6080;
      border-radius: 4px;
      transition: color 120ms ease;
      padding: 0;

      &:hover { color: #8A9BB0; }
    }

    /* ── Options row ─────────────────────────────────────────────────────────── */
    .login-options {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-top: 0.125rem;
    }

    .remember-label {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      cursor: pointer;
      font-size: 0.875rem;
      color: #C8D4E3;
      user-select: none;
    }

    .remember-check {
      appearance: none;
      -webkit-appearance: none;
      width: 16px;
      height: 16px;
      border: 1.5px solid #2563EB;
      border-radius: 3px;
      background: transparent;
      cursor: pointer;
      flex-shrink: 0;
      position: relative;
      transition: background 120ms ease;

      &:checked {
        background: #2563EB;

        &::after {
          content: '';
          position: absolute;
          left: 3px;
          top: 0px;
          width: 5px;
          height: 9px;
          border: 2px solid #fff;
          border-top: none;
          border-left: none;
          transform: rotate(45deg);
        }
      }
    }

    .forgot-link {
      font-size: 0.875rem;
      color: #3B82F6;
      text-decoration: none;
      transition: color 120ms ease;

      &:hover { color: #60A5FA; text-decoration: underline; }
    }

    /* ── Error message ───────────────────────────────────────────────────────── */
    .login-error {
      padding: 0.625rem 0.875rem;
      background: rgba(239, 68, 68, 0.12);
      border: 1px solid rgba(239, 68, 68, 0.35);
      border-radius: 6px;
      font-size: 0.8125rem;
      color: #FCA5A5;
    }

    /* ── Buttons ─────────────────────────────────────────────────────────────── */
    .btn-primary {
      width: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      padding: 0.8125rem;
      margin-top: 0.5rem;
      background: #2563EB;
      border: none;
      border-radius: 8px;
      color: #FFFFFF;
      font-size: 1rem;
      font-weight: 600;
      letter-spacing: 0.02em;
      cursor: pointer;
      transition: background 150ms ease, box-shadow 150ms ease;

      &:hover:not(:disabled) {
        background: #1D4ED8;
        box-shadow: 0 4px 20px rgba(37, 99, 235, 0.4);
      }

      &:active:not(:disabled) { background: #1E40AF; }

      &:disabled {
        opacity: 0.65;
        cursor: not-allowed;
      }
    }

    .btn-spinner {
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    /* ── Divider ─────────────────────────────────────────────────────────────── */
    .login-divider {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      margin: 1.25rem 0;
      color: #3D5270;
      font-size: 0.8125rem;

      &::before,
      &::after {
        content: '';
        flex: 1;
        height: 1px;
        background: rgba(36, 51, 80, 0.8);
      }
    }

    /* ── SSO button ──────────────────────────────────────────────────────────── */
    .btn-sso {
      width: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.625rem;
      padding: 0.75rem;
      background: transparent;
      border: 1px solid rgba(36, 51, 80, 0.9);
      border-radius: 8px;
      color: #C8D4E3;
      font-size: 0.9375rem;
      font-weight: 500;
      cursor: pointer;
      transition: border-color 150ms ease, background 150ms ease, color 150ms ease;

      &:hover {
        border-color: #2563EB;
        background: rgba(37, 99, 235, 0.08);
        color: #FFFFFF;
      }
    }

    /* ── Footer ──────────────────────────────────────────────────────────────── */
    .login-footer {
      margin: 1.5rem 0 0;
      text-align: center;
      font-size: 0.75rem;
      color: #3D5270;
    }

    /* ── Responsive ──────────────────────────────────────────────────────────── */
    @media (max-width: 768px) {
      .login-page {
        grid-template-columns: 1fr;
      }
      .login-branding { display: none; }
      .login-panel {
        padding: 1.5rem 1rem;
        background: rgba(6, 14, 32, 0.7);
      }
    }
  `],
})
export class LoginComponent {
  private readonly oauthService = inject(OAuthService);
  private readonly router = inject(Router);

  usernameValue = '';
  passwordValue = '';
  showPassword = false;
  rememberMe = false;
  isLoading = false;
  errorMessage = '';

  async signIn(): Promise<void> {
    this.errorMessage = '';
    if (!this.usernameValue.trim()) {
      this.errorMessage = 'Please enter your username.';
      return;
    }
    if (!this.passwordValue) {
      this.errorMessage = 'Please enter your password.';
      return;
    }
    this.isLoading = true;
    try {
      await this.oauthService.fetchTokenUsingPasswordFlow(
        this.usernameValue.trim(),
        this.passwordValue
      );
      await this.router.navigateByUrl('/dashboard');
    } catch {
      this.errorMessage = 'Invalid username or password. Please try again.';
    } finally {
      this.isLoading = false;
    }
  }

  signInSSO(): void {
    this.oauthService.initLoginFlow();
  }
}
