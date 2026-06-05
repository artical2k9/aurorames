export const environment = {
  // Session timeout applied to every user login.
  // Must match keycloak/mes-realm.json ssoSessionMaxLifespan (seconds × 1000).
  sessionTimeoutMs: 4 * 60 * 60 * 1000,

  // How far before access-token expiry the silent refresh fires.
  tokenRefreshMarginMs: 60 * 1000,
};
