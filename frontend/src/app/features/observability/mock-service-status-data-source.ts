import { Injectable } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { ServiceStatus, SERVICE_LABELS } from './service-status.model';
import { ServiceStatusDataSource } from './service-status-data-source';

/**
 * Source simulée de la page Observabilité.
 * Produit un état par service (UP / DOWN / UNKNOWN) horodaté à l'instant de
 * l'appel, pour que la page montre une heure de dernier contrôle plausible.
 * Le mode `unavailable` permet de tester le message « Supervision indisponible ».
 */
@Injectable()
export class MockServiceStatusDataSource implements ServiceStatusDataSource {
  /** 'unavailable' fait échouer l'appel, comme un outil de supervision muet. */
  mode: 'normal' | 'unavailable' = 'normal';

  /** État forcé pour un service donné (utile en test : forcer un DOWN). */
  forcedState?: { key: keyof typeof SERVICE_LABELS; state: 'UP' | 'DOWN' | 'UNKNOWN' };

  getStatuses(): Observable<ServiceStatus[]> {
    if (this.mode === 'unavailable') {
      return throwError(() => new Error('Supervision indisponible'));
    }

    const now = new Date().toISOString();
    const states: Record<string, ServiceStatus['state']> = {
      telemetry: 'UP',
      assets: 'UP',
      detection: 'UP',
      incidents: 'DOWN',
      notifications: 'UP',
      gateway: 'UNKNOWN',
    };

    if (this.forcedState) {
      states[this.forcedState.key] = this.forcedState.state;
    }

    const statuses: ServiceStatus[] = (
      Object.keys(SERVICE_LABELS) as Array<keyof typeof SERVICE_LABELS>
    ).map((key) => ({
      key,
      label: SERVICE_LABELS[key],
      state: states[key],
      checkedAt: now,
    }));

    return of(statuses);
  }
}