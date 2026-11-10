import { AuthConfig } from 'angular-oauth2-oidc';
import { AppConfig } from './app-config';

// Realm "aquaops", client public "aquaops-frontend". Les adresses viennent de /config.json.
export function buildAuthConfig(cfg: AppConfig): AuthConfig {
  return {
    issuer: cfg.issuer,
    clientId: cfg.clientId,
    redirectUri: window.location.origin + '/login',
    postLogoutRedirectUri: window.location.origin + '/login',
    responseType: 'code', // Authorization Code + PKCE (pas de secret dans le navigateur)
    scope: 'openid profile email',
    requireHttps: cfg.issuer.startsWith('https://'),
    showDebugInformation: false,
  };
}