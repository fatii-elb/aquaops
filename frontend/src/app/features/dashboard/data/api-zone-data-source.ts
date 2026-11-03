import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, forkJoin, map, of, switchMap } from 'rxjs';
import { ZoneDataSource } from './zone-data-source';
import { ZONE_DATA_SOURCE_CONFIG } from './zone-data-source.config';
import { PumpState, ZoneSummary, ZoneValues } from './zone.model';
import { computeZoneState } from './zone-state';

interface ApiZone {
  id: number;
  name: string;
}

interface ApiSensor {
  id: number;
  name: string;
  type: 'PRESSURE' | 'FLOW' | 'LEVEL' | 'PUMP_STATE';
  zoneId: number;
}

interface ApiReading {
  sensorId: number;
  value: number | null;
  pumpState: string | null;
  measuredAt: string;
}

/** Réponse paginée de GET /api/telemetry/readings. */
interface ApiPage<T> {
  content: T[];
}

/** Dernière mesure connue d'un capteur. */
interface Latest {
  sensor: ApiSensor;
  reading: ApiReading;
}

/** Les listes du service des actifs sont des tableaux ; on accepte aussi une page { content }. */
type ListOrPage<T> = T[] | { content?: T[] };

function asList<T>(response: ListOrPage<T>): T[] {
  return Array.isArray(response) ? response : (response.content ?? []);
}

/**
 * Vraies données : zones et capteurs depuis le service des actifs, dernière mesure de chaque
 * capteur depuis le service de télémétrie. L'API des mesures interroge un capteur à la fois
 * (GET /api/telemetry/readings?sensor=&from=&to=), d'où une requête par capteur à chaque rafraîchissement.
 */
@Injectable()
export class ApiZoneDataSource extends ZoneDataSource {
  private http = inject(HttpClient);
  private config = ZONE_DATA_SOURCE_CONFIG;

  getZones(): Observable<ZoneSummary[]> {
    return forkJoin({
      zones: this.http.get<ListOrPage<ApiZone>>(`${this.config.assetsBaseUrl}/zones`).pipe(map(asList)),
      sensors: this.http.get<ListOrPage<ApiSensor>>(`${this.config.assetsBaseUrl}/sensors`).pipe(map(asList)),
    }).pipe(
      switchMap(({ zones, sensors }) =>
        this.latestReadings(sensors).pipe(map((latest) => zones.map((zone) => this.toSummary(zone, latest)))),
      ),
    );
  }

  private latestReadings(sensors: ApiSensor[]): Observable<Latest[]> {
    if (sensors.length === 0) {
      return of([]); // forkJoin([]) n'émet rien : on évite ce cas
    }
    const to = new Date();
    const from = new Date(to.getTime() - this.config.lookbackSeconds * 1000);
    return forkJoin(sensors.map((sensor) => this.latest(sensor, from, to))).pipe(
      map((all) => all.filter((item): item is Latest => item !== null)),
    );
  }

  /** Une panne sur un capteur ne doit pas casser tout le tableau de bord : on renvoie null. */
  private latest(sensor: ApiSensor, from: Date, to: Date): Observable<Latest | null> {
    const params = new HttpParams()
      .set('sensor', sensor.id)
      .set('from', from.toISOString())
      .set('to', to.toISOString())
      .set('size', 1000);
    return this.http.get<ApiPage<ApiReading>>(`${this.config.telemetryBaseUrl}/readings`, { params }).pipe(
      // Les mesures sont triées de la plus ancienne à la plus récente : la dernière est la plus récente.
      map((page) => {
        const last = page.content.at(-1);
        return last ? { sensor, reading: last } : null;
      }),
      catchError(() => of(null)),
    );
  }

  private toSummary(zone: ApiZone, latest: Latest[]): ZoneSummary {
    const mine = latest.filter((item) => item.sensor.zoneId === zone.id);
    const numbers = (type: ApiSensor['type']) =>
      mine
        .filter((item) => item.sensor.type === type && item.reading.value !== null)
        .map((item) => item.reading.value as number);

    // Cas défavorable retenu quand une zone a plusieurs capteurs du même type :
    // pression la plus basse, débit le plus haut, niveau le plus bas.
    const pressure = numbers('PRESSURE');
    const flow = numbers('FLOW');
    const level = numbers('LEVEL');
    const pumps = mine.filter((item) => item.sensor.type === 'PUMP_STATE');
    const pumpState: PumpState | null =
      pumps.length === 0 ? null : pumps.some((p) => p.reading.pumpState === 'FAULT') ? 'FAULT' : 'NORMAL';

    const values: ZoneValues = {
      pressureBar: pressure.length ? Math.min(...pressure) : null,
      flowM3h: flow.length ? Math.max(...flow) : null,
      levelM: level.length ? Math.min(...level) : null,
      pumpState,
    };
    const times = mine.map((item) => item.reading.measuredAt).sort();
    return {
      id: zone.id,
      name: zone.name,
      ...values,
      state: computeZoneState(values),
      updatedAt: times.at(-1) ?? new Date().toISOString(),
    };
  }
}
