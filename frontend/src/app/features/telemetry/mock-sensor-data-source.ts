import { Injectable } from '@angular/core';
import { Observable, defer, of } from 'rxjs';
import { SensorReading, SensorRow, SensorState, SensorType } from './sensor.model';

const HISTORY_SENSORS: Record<
  number,
  { type: SensorType; value: number | null; unit: string | null }
> = {
  1: { type: 'PRESSURE', value: 2.4, unit: 'bar' },
  2: { type: 'LEVEL', value: 3.2, unit: 'm' },
  3: { type: 'PUMP_STATE', value: null, unit: null },
  7: { type: 'FLOW', value: 18.6, unit: 'm³/h' },
};

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

  getHistory(sensorId: number, from: Date, to: Date): Observable<SensorReading[]> {
    return defer(() => {
      const sensor = HISTORY_SENSORS[sensorId];
      if (!sensor) {
        return of([]);
      }

      const start = from.getTime();
      const end = to.getTime();
      if (!Number.isFinite(start) || !Number.isFinite(end) || start > end) {
        throw new RangeError('La période de consultation est invalide.');
      }

      const count = start === end ? 1 : 24;
      const readings = Array.from({ length: count }, (_, index) => {
        const fraction = count === 1 ? 0 : index / (count - 1);
        const measuredAt = new Date(start + fraction * (end - start)).toISOString();
        const value =
          sensor.value === null
            ? null
            : Number((sensor.value + Math.sin(index / 3) * sensor.value * 0.08).toFixed(2));
        return {
          value,
          unit: sensor.unit,
          pumpState:
            sensor.type === 'PUMP_STATE' && index % 12 === 0
              ? 'FAULT'
              : sensor.type === 'PUMP_STATE'
                ? 'NORMAL'
                : null,
          measuredAt,
        };
      });

      return of(readings);
    });
  }
}
