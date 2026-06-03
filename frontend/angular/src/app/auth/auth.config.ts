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
  // Password-flow apps do not go through the PKCE redirect cycle, so
  // disable OIDC id_token processing: the library would try to validate the
  // nonce that was never created for this flow, causing a silent
  // "invalid_nonce_in_state" rejection and leaving the token unreachable.
  oidc: false,
};
