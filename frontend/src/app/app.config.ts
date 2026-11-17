import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideOAuthClient } from 'angular-oauth2-oidc';

import { routes } from './app.routes';
import { AuthService } from './core/auth.service';
import { authInterceptor } from './core/auth.interceptor';
import { forbiddenInterceptor } from './core/forbidden.interceptor';
import { ApiZoneDataSource } from './features/dashboard/data/api-zone-data-source';
import { MockZoneDataSource } from './features/dashboard/data/mock-zone-data-source';
import { ZONE_DATA_SOURCE_CONFIG } from './features/dashboard/data/zone-data-source.config';
import { ZoneDataSource } from './features/dashboard/data/zone-data-source';
import { ApiAuditDataSource } from './features/audit/api-audit-data-source';
import { AuditDataSource } from './features/audit/audit-data-source';
import { AUDIT_DATA_SOURCE_CONFIG } from './features/audit/audit-data-source.config';
import { MockAuditDataSource } from './features/audit/mock-audit-data-source';
import { ConfiguredSensorDataSource } from './features/telemetry/configured-sensor-data-source';
import { SensorDataSource } from './features/telemetry/sensor-data-source';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor, forbiddenInterceptor])),
    provideOAuthClient(),
    // Source des donnees du dashboard : 'mock' ou 'api' (voir zone-data-source.config.ts).
    { provide: ZoneDataSource, useClass: ZONE_DATA_SOURCE_CONFIG.mode === 'api' ? ApiZoneDataSource : MockZoneDataSource },
    // Source des donnees du journal d'audit : 'api' ou 'mock' (voir audit-data-source.config.ts).
    { provide: AuditDataSource, useClass: AUDIT_DATA_SOURCE_CONFIG.mode === 'api' ? ApiAuditDataSource : MockAuditDataSource },
    { provide: SensorDataSource, useClass: ConfiguredSensorDataSource },
    // Avant d'afficher la moindre page : contacter Keycloak et lire le retour de connexion.
    provideAppInitializer(() => inject(AuthService).init()),
  ],
};
