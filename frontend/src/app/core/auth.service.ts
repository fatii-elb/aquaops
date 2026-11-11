import { Injectable, inject } from '@angular/core';
import { OAuthService } from 'angular-oauth2-oidc';
import { buildAuthConfig } from './auth.config';
import { loadAppConfig } from './app-config';

interface TokenPayload {
  preferred_username?: string;
  realm_access?: { roles?: string[] };
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private oauth = inject(OAuthService);

  /** Appelé une fois au démarrage de l'application (voir app.config.ts). */
  async init(): Promise<void> {
    this.oauth.configure(buildAuthConfig(await loadAppConfig()));
    try {
      await this.oauth.loadDiscoveryDocumentAndTryLogin();
      this.oauth.setupAutomaticSilentRefresh();
    } catch (error) {
      console.error('Keycloak injoignable ou configuration invalide', error);
    }
  }

  /** Redirige vers la page de connexion de Keycloak. */
  login(): void {
    this.oauth.initCodeFlow();
  }

  logout(): void {
    this.oauth.logOut();
  }

  isLoggedIn(): boolean {
    return this.oauth.hasValidAccessToken();
  }

  get username(): string {
    return this.payload()?.preferred_username ?? '';
  }

  roles(): string[] {
    return this.payload()?.realm_access?.roles ?? [];
  }

  hasAnyRole(wanted: string[]): boolean {
    const mine = this.roles();
    return wanted.some((role) => mine.includes(role));
  }

  /** Lit le contenu (payload) du jeton d'accès. Le serveur reste seul juge de la validité du jeton. */
  private payload(): TokenPayload | null {
    const token = this.oauth.getAccessToken();
    if (!token) {
      return null;
    }
    try {
      const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      return JSON.parse(atob(base64)) as TokenPayload;
    } catch {
      return null;
    }
  }
}
