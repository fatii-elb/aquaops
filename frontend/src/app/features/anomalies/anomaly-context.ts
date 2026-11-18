import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, of } from 'rxjs';
import { Incident, ZoneInfo } from '../incidents/incident.model';
import { IncidentApi } from '../incidents/incident-api';
import { ZoneNames } from '../incidents/zone-names';

interface SensorInfo {
  id: number;
  name: string;
}

/**
 * Données qui complètent une anomalie : noms de zone et de capteur (service des actifs) et incident associé
 * (service des incidents). Aucune de ces sources n'est indispensable : en cas d'échec, l'écran affiche les
 * identifiants et l'absence d'incident.
 */
@Injectable({ providedIn: 'root' })
export class AnomalyContext {
  private http = inject(HttpClient);
  private zoneNames = inject(ZoneNames);
  private incidentApi = inject(IncidentApi);
  private sensorsRequested = false;

  readonly sensors = signal<SensorInfo[]>([]);

  /** Charge les noms une seule fois. */
  loadNames(): void {
    this.zoneNames.load();
    if (this.sensorsRequested) {
      return;
    }
    this.sensorsRequested = true;
    this.http
      .get<SensorInfo[] | { content?: SensorInfo[] }>('/api/assets/sensors')
      .pipe(catchError(() => of([] as SensorInfo[])))
      .subscribe((r) => this.sensors.set(Array.isArray(r) ? r : (r.content ?? [])));
  }

  zones(): ZoneInfo[] {
    return this.zoneNames.zones();
  }

  zoneName(id: number): string {
    return this.zoneNames.name(id);
  }

  sensorName(id: number | null): string {
    if (id === null) {
      return '—';
    }
    return this.sensors().find((s) => s.id === id)?.name ?? `Capteur ${id}`;
  }

  /** Incidents actuels : à recharger à chaque rafraîchissement, car un incident peut être créé entre-temps. */
  incidents$(): Observable<Incident[]> {
    return this.incidentApi.list().pipe(catchError(() => of([] as Incident[])));
  }
}
