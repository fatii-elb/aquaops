import { ALL_STATUSES, STAGES, delaySeconds, formatDateTime, incidentCode, nextStatuses, priorityLabel, sortIncidents, statusLabel, timeAgo, typeLabel } from './incident-rules';
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

  it('le code de l\'incident reprend l\'année et l\'identifiant sur 3 chiffres', () => {
    expect(incidentCode({ id: 89, createdAt: '2026-11-09T10:00:00Z' })).toBe('INC-2026-089');
    expect(incidentCode({ id: 1234, createdAt: '2026-11-09T10:00:00Z' })).toBe('INC-2026-1234');
    expect(incidentCode({ id: 5, createdAt: 'nope' })).toBe('INC------005');
  });

  it('libellés de la maquette : Affecté et Élevée', () => {
    expect(statusLabel('ASSIGNED')).toBe('Affecté');
    expect(priorityLabel('HIGH')).toBe('Élevée');
  });

  it('la barre de pipeline suit les six étapes dans l\'ordre', () => {
    expect(STAGES.map((s) => s.status)).toEqual(ALL_STATUSES);
  });

  it('timeAgo : minutes, heures, jours', () => {
    const now = new Date('2026-11-09T12:00:00Z').getTime();
    expect(timeAgo('2026-11-09T11:59:40Z', now)).toBe("à l'instant");
    expect(timeAgo('2026-11-09T11:38:00Z', now)).toBe('il y a 22 min');
    expect(timeAgo('2026-11-09T10:15:00Z', now)).toBe('il y a 1 h 45 min');
    expect(timeAgo('2026-11-06T12:00:00Z', now)).toBe('il y a 3 j');
    expect(timeAgo(null, now)).toBe('—');
  });

  it('delaySeconds : écart entre la mesure et la création', () => {
    expect(delaySeconds('2026-11-09T10:00:00Z', '2026-11-09T10:00:05Z')).toBe(5);
    expect(delaySeconds(null, '2026-11-09T10:00:05Z')).toBeNull();
  });
});
