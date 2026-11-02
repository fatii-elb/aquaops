import { Injectable } from '@angular/core';
import { Observable, defer, of } from 'rxjs';
import { ZoneDataSource } from './zone-data-source';
import { ZoneSummary, ZoneValues } from './zone.model';
import { computeZoneState } from './zone-state';

const ZONES = [
  { id: 1, name: 'Zone 1 · Centre-ville' },
  { id: 2, name: 'Zone 2 · Médina' },
  { id: 3, name: 'Zone 3 · Industrielle' },
  { id: 4, name: 'Zone 4 · Gueliz' },
  { id: 5, name: 'Zone 5 · Palmeraie' },
];

/**
 * Données simulées, déterministes (pas de hasard : tests stables).
 * Les valeurs oscillent doucement ; la zone 3 simule une fuite tous les 12 appels (pression basse, débit haut).
 */
@Injectable()
export class MockZoneDataSource extends ZoneDataSource {
  private tick = 0;

  getZones(): Observable<ZoneSummary[]> {
    return defer(() => {
      this.tick++;
      const now = new Date().toISOString();
      return of(
        ZONES.map(({ id, name }) => {
          const values = this.valuesFor(id);
          return { id, name, ...values, state: computeZoneState(values), updatedAt: now };
        }),
      );
    });
  }

  private valuesFor(id: number): ZoneValues {
    const wave = Math.sin(this.tick / 3 + id);
    const leak = id === 3 && this.tick % 12 >= 8;
    return {
      pressureBar: round(leak ? 1.1 : 2.6 + 0.3 * wave),
      flowM3h: round(leak ? 48 : 30 + 4 * wave),
      levelM: round(3.5 + 0.4 * wave),
      pumpState: id === 5 && this.tick % 20 >= 17 ? 'FAULT' : 'NORMAL',
    };
  }
}

function round(value: number): number {
  return Math.round(value * 10) / 10;
}
