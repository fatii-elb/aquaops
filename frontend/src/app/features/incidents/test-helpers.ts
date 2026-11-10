import { EventSourceLike } from '../../core/notification-stream';
import { HistoryEntry, Incident } from './incident.model';

/** Faux flux SSE pour les tests : aucune connexion réseau. */
export class FakeEventSource implements EventSourceLike {
  onopen: ((e: Event) => void) | null = null;
  onerror: ((e: Event) => void) | null = null;
  onmessage: ((e: MessageEvent) => void) | null = null;
  close() {}
  push(data: string) {
    this.onmessage?.({ data } as MessageEvent);
  }
}

export const sampleIncident: Incident = {
  id: 7,
  anomalyId: '7b0f2a3e-4c1d-4e6a-9d52-1f3c8a9b0e11',
  zoneId: 3,
  sensorId: 'S07',
  type: 'LEAK_SUSPECTED',
  status: 'OPEN',
  priority: 'CRITICAL',
  description: 'Chute de pression',
  assignee: null,
  measuredAt: '2026-11-09T09:59:55Z',
  createdAt: '2026-11-09T10:00:00Z',
  updatedAt: '2026-11-09T10:00:00Z',
};

export const sampleEntry: HistoryEntry = {
  id: 1,
  incidentId: 7,
  action: 'CONFIRMED',
  fromStatus: 'OPEN',
  toStatus: 'CONFIRMED',
  author: 'test-superviseur',
  comment: null,
  occurredAt: '2026-11-09T10:05:00Z',
};
