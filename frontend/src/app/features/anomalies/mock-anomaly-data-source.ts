import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { AnomalyDataSource } from './anomaly-data-source';
import { Anomaly, AnomalyPage, AnomalyQuery } from './anomaly.model';
import { applyQuery } from './anomaly-rules';

const at = (hhmmss: string) => `2026-11-14T${hhmmss}Z`;

/** Jeu de démonstration, mêmes règles de filtre, tri et pagination que le serveur. */
export const MOCK_ANOMALIES: Anomaly[] = [
  { id: 'a-001', type: 'LEAK_SUSPECTED', zoneId: 1, sensorId: 1, severity: 'CRITICAL', status: 'OPEN', measuredAt: at('09:58:10'), detectedAt: at('09:58:12'), resolvedAt: null },
  { id: 'a-002', type: 'PUMP_FAULT', zoneId: 2, sensorId: 4, severity: 'HIGH', status: 'OPEN', measuredAt: at('09:40:00'), detectedAt: at('09:40:01'), resolvedAt: null },
  { id: 'a-003', type: 'SENSOR_SILENT', zoneId: 1, sensorId: 2, severity: 'MEDIUM', status: 'RESOLVED', measuredAt: at('09:10:00'), detectedAt: at('09:10:12'), resolvedAt: at('09:12:30') },
  { id: 'a-004', type: 'LEAK_SUSPECTED', zoneId: 3, sensorId: 7, severity: 'CRITICAL', status: 'RESOLVED', measuredAt: at('08:30:00'), detectedAt: at('08:30:03'), resolvedAt: at('08:41:00') },
  { id: 'a-005', type: 'PUMP_FAULT', zoneId: 1, sensorId: 3, severity: 'HIGH', status: 'RESOLVED', measuredAt: at('08:05:00'), detectedAt: at('08:05:01'), resolvedAt: at('08:06:00') },
  { id: 'a-006', type: 'SENSOR_SILENT', zoneId: 2, sensorId: 5, severity: 'MEDIUM', status: 'OPEN', measuredAt: at('07:50:00'), detectedAt: at('07:50:11'), resolvedAt: null },
  { id: 'a-007', type: 'LOW_LEVEL', zoneId: 3, sensorId: 8, severity: 'LOW', status: 'RESOLVED', measuredAt: at('07:20:00'), detectedAt: at('07:20:02'), resolvedAt: at('07:45:00') },
];

@Injectable()
export class MockAnomalyDataSource extends AnomalyDataSource {
  list(query: AnomalyQuery): Observable<AnomalyPage> {
    return of(applyQuery(MOCK_ANOMALIES, query));
  }
}
