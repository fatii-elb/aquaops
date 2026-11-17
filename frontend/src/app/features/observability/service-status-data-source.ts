import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { ServiceStatus } from './service-status.model';

/**
 * Jeton d'injection + contrat de la source de données de la page Observabilité.
 * Même principe que reports-data-source.ts : une implémentation Mock, une
 * implémentation Api (plus tard), choisie dans les providers de la route.
 */
export abstract class ServiceStatusDataSource {
  /** Renvoie l'état courant de tous les services supervisés. */
  abstract getStatuses(): Observable<ServiceStatus[]>;
}

export const SERVICE_STATUS_DATA_SOURCE =
  new InjectionToken<ServiceStatusDataSource>('ServiceStatusDataSource');

/** Mode choisi dans les providers de la route (mock pour l'instant). */
export const SERVICE_STATUS_DATA_SOURCE_CONFIG: {
  mode: 'api' | 'mock';
} = { mode: 'mock' };