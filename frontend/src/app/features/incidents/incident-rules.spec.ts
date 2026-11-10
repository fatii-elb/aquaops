import { ALL_STATUSES, formatDateTime, nextStatuses, priorityLabel, sortIncidents, statusLabel, typeLabel } from './incident-rules';
import { Incident, IncidentStatus } from './incident.model';

const incident = (over: Partial<Incident>): Incident => ({
  id: 1,
  anomalyId: null,
  zoneId: 1,
  sensorId: null,
  type: 'LEAK_SUSPECTED',
  status: 'OPEN',
  priority: 'MEDIUM',
  description: null,
  assignee: null,
  measuredAt: null,
  createdAt: '2026-11-09T10:00:00Z',
  updatedAt: '2026-11-09T10:00:00Z',
  ...over,
});

describe('incident-rules', () => {
  it('propose exactement les 9 transitions du serveur', () => {
    const pairs = ALL_STATUSES.flatMap((from) => nextStatuses(from).map((to) => `${from}>${to}`));
    expect(pairs.sort()).toEqual(
      [
        'OPEN>CONFIRMED',
        'OPEN>CLOSED',
        'CONFIRMED>ASSIGNED',
        'CONFIRMED>CLOSED',
        'ASSIGNED>IN_PROGRESS',
        'ASSIGNED>CLOSED',
        'IN_PROGRESS>RESOLVED',
        'IN_PROGRESS>CLOSED',
        'RESOLVED>CLOSED',
      ].sort(),
    );
  });

  it("n'offre aucune action sur un incident clos", () => {
    expect(nextStatuses('CLOSED')).toEqual([]);
  });

  it('trie : critique avant faible, plus récent avant plus ancien, clos en dernier', () => {
    const sorted = sortIncidents([
      incident({ id: 1, priority: 'LOW' }),
      incident({ id: 2, priority: 'CRITICAL', status: 'CLOSED' }),
      incident({ id: 3, priority: 'CRITICAL', createdAt: '2026-11-09T09:00:00Z' }),
      incident({ id: 4, priority: 'CRITICAL', createdAt: '2026-11-09T11:00:00Z' }),
    ]);
    expect(sorted.map((i) => i.id)).toEqual([4, 3, 1, 2]);
  });

  it('ne modifie pas le tableau reçu', () => {
    const list = [incident({ id: 1, priority: 'LOW' }), incident({ id: 2, priority: 'HIGH' })];
    sortIncidents(list);
    expect(list.map((i) => i.id)).toEqual([1, 2]);
  });

  it('donne un libellé français à chaque statut, priorité et type connus', () => {
    expect(statusLabel('IN_PROGRESS')).toBe('En cours');
    expect(priorityLabel('CRITICAL')).toBe('Critique');
    expect(typeLabel('SENSOR_SILENT')).toBe('Capteur silencieux');
    expect(typeLabel('AUTRE')).toBe('AUTRE');
  });

  it('formatDateTime tolère une date absente ou invalide', () => {
    expect(formatDateTime(null)).toBe('—');
    expect(formatDateTime('pas une date')).toBe('—');
    expect(formatDateTime('2026-11-09T10:00:00Z')).toContain('2026');
  });

  it('chaque statut atteignable est un vrai statut', () => {
    const all = new Set<IncidentStatus>(ALL_STATUSES);
    ALL_STATUSES.forEach((s) => nextStatuses(s).forEach((t) => expect(all.has(t)).toBe(true)));
  });
});
