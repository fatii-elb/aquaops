import { Injectable } from '@angular/core';
import { Observable, defer, of } from 'rxjs';
import { SensorRow, SensorState } from './sensor.model';

@Injectable({ providedIn: 'root' })
export class MockSensorDataSource {
  getSensors(silenceThresholdSeconds: number): Observable<SensorRow[]> {
    return defer(() => {
      const now = Date.now();
      const stamp = (secondsAgo: number) => new Date(now - secondsAgo * 1000).toISOString();
      const samples: Omit<SensorRow, 'state'>[] = [
        {
          id: 1,
          name: 'Pression principale',
          type: 'PRESSURE',
          zoneId: 1,
          zoneName: 'Centre-ville',
          assetName: 'Conduite principale',
          latestValue: 2.4,
          unit: 'bar',
          pumpState: null,
          measuredAt: stamp(2),
        },
        {
          id: 2,
          name: 'Niveau réservoir',
          type: 'LEVEL',
          zoneId: 2,
          zoneName: 'Médina',
          assetName: 'Réservoir Nord',
          latestValue: 3.2,
          unit: 'm',
          pumpState: null,
          measuredAt: stamp(4),
        },
        {
          id: 3,
          name: 'Pompe de relevage',
          type: 'PUMP_STATE',
          zoneId: 3,
          zoneName: 'Zone industrielle',
          assetName: 'Pompe P-03',
          latestValue: null,
          unit: null,
          pumpState: 'FAULT',
          measuredAt: stamp(3),
        },
        {
          id: 7,
          name: 'Débit secondaire',
          type: 'FLOW',
          zoneId: 1,
          zoneName: 'Centre-ville',
          assetName: 'Conduite secondaire',
          latestValue: 18.6,
          unit: 'm³/h',
          pumpState: null,
          measuredAt: stamp(Math.max(silenceThresholdSeconds + 1, 20)),
        },
      ];
      return of(samples.map((sensor) => {
        const ageSeconds = sensor.measuredAt
          ? (now - new Date(sensor.measuredAt).getTime()) / 1000
          : Infinity;
        const state: SensorState = ageSeconds <= silenceThresholdSeconds ? 'active' : 'silent';
        return { ...sensor, state };
      }));
    });
  }
}
