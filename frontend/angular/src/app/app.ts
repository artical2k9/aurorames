import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';
import { ThemeToggleComponent } from './shared/theme';
import { ThemeService } from './shared/theme';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ThemeToggleComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App implements OnInit {
  protected readonly title = signal('MES');
  private readonly oauthService = inject(OAuthService);
  private readonly themeService = inject(ThemeService);

  get isLoggedIn(): boolean {
    return this.oauthService.hasValidAccessToken();
  }

  ngOnInit(): void {
    this.themeService.init();
  }

  login(): void {
    this.oauthService.initLoginFlow();
  }

  logout(): void {
    this.oauthService.logOut();
  }
}
