import { AuthConfig } from 'angular-oauth2-oidc';

export const authConfig: AuthConfig = {
  issuer: 'http://localhost:8080/realms/mes',
  redirectUri: window.location.origin,
  clientId: 'mes-frontend',
  responseType: 'code',
  scope: 'openid',
  showDebugInformation: false,
  requireHttps: false,
  skipIssuerCheck: true,
  strictDiscoveryDocumentValidation: false,
};
