import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideOAuthClient } from 'angular-oauth2-oidc';

import { routes } from './app.routes';
import { AuthService } from './core/auth.service';
import { MockZoneDataSource } from './features/dashboard/data/mock-zone-data-source';
import { ZoneDataSource } from './features/dashboard/data/zone-data-source';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(),
    provideOAuthClient(),
    // Source des donnees du dashboard : mock pour l'instant, API ensuite (SCRUM-29, partie 2).
    { provide: ZoneDataSource, useClass: MockZoneDataSource },
    // Avant d'afficher la moindre page : contacter Keycloak et lire le retour de connexion.
    provideAppInitializer(() => inject(AuthService).init()),
  ],
};
