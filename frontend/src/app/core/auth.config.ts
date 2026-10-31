import { AuthConfig } from 'angular-oauth2-oidc';

// Keycloak local (conteneur Docker, port 8180), realm "aquaops", client public "aquaops-frontend".
export const authConfig: AuthConfig = {
  issuer: 'http://localhost:8180/realms/aquaops',
  clientId: 'aquaops-frontend',
  redirectUri: window.location.origin + '/login',
  postLogoutRedirectUri: window.location.origin + '/login',
  responseType: 'code', // flux "Authorization Code" avec PKCE (pas de secret dans le navigateur)
  scope: 'openid profile email',
  requireHttps: false, // http://localhost en développement uniquement
  showDebugInformation: false,
};
