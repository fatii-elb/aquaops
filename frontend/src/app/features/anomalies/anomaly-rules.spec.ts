import { describe, expect, it } from 'vitest';
import { Incident } from '../incidents/incident.model';
import { Anomaly, AnomalyQuery, DEFAULT_QUERY } from './anomaly.model';
import {
  applyQuery,
  formatDuration,
  fromLocalInput,
  incidentsByAnomaly,
  matchesSearch,
  nextSort,
  normalize,
  secondsBetween,
  toLocalInput,
  toParams,
} from './anomaly-rules';

const base: Omit<Anomaly, 'id' | 'type' | 'zoneId' | 'status' | 'detectedAt'> = {
  sensorId: 1,
  severity: 'HIGH',
  measuredAt: null,
  resolvedAt: null,
};

const ALL: Anomaly[] = [
  { ...base, id: 'a1', type: 'LEAK_SUSPECTED', zoneId: 1, status: 'OPEN', detectedAt: '2026-11-14T10:00:00Z', severity: 'CRITICAL' },
  { ...base, id: 'a2', type: 'PUMP_FAULT', zoneId: 1, status: 'RESOLVED', detectedAt: '2026-11-14T10:05:00Z' },
  { ...base, id: 'a3', type: 'SENSOR_SILENT', zoneId: 2, status: 'OPEN', detectedAt: '2026-11-14T10:10:00Z', severity: 'MEDIUM' },
  { ...base, id: 'a4', type: 'LEAK_SUSPECTED', zoneId: 2, status: 'RESOLVED', detectedAt: '2026-11-14T10:15:00Z', severity: 'CRITICAL' },
];

const q = (patch: Partial<AnomalyQuery> = {}): AnomalyQuery => ({ ...DEFAULT_QUERY, ...patch });
const ids = (patch: Partial<AnomalyQuery> = {}) => applyQuery(ALL, q(patch)).content.map((a) => a.id);

const names = {
  zoneName: (id: number) => (id === 1 ? 'Zone Nord' : `Zone ${id}`),
  sensorName: (id: number | null) => (id === null ? '—' : `Capteur ${id}`),
};

describe('toParams', () => {
  it('envoie toujours page, taille et tri, et seulement les filtres renseignés', () => {
    expect(toParams(q())).toEqual({ page: '0', size: '20', sort: 'detectedAt,desc' });
    expect(toParams(q({ zoneId: 3, type: 'PUMP_FAULT', status: 'OPEN', from: '2026-11-14T00:00:00Z', to: '2026-11-15T00:00:00Z', page: 2, size: 50 }))).toEqual({
      page: '2', size: '50', sort: 'detectedAt,desc', zoneId: '3', type: 'PUMP_FAULT', status: 'OPEN',
      from: '2026-11-14T00:00:00Z', to: '2026-11-15T00:00:00Z',
    });
  });

  it('une zone 0 est un filtre valide', () => {
    expect(toParams(q({ zoneId: 0 }))['zoneId']).toBe('0');
  });
});

describe('nextSort', () => {
  it('inverse la direction sur la même colonne', () => {
    expect(nextSort({ field: 'type', direction: 'asc' }, 'type')).toEqual({ field: 'type', direction: 'desc' });
    expect(nextSort({ field: 'type', direction: 'desc' }, 'type')).toEqual({ field: 'type', direction: 'asc' });
  });

  it('change de colonne : croissant, sauf la date (récent d’abord)', () => {
    expect(nextSort({ field: 'type', direction: 'desc' }, 'zoneId')).toEqual({ field: 'zoneId', direction: 'asc' });
    expect(nextSort({ field: 'type', direction: 'asc' }, 'detectedAt')).toEqual({ field: 'detectedAt', direction: 'desc' });
  });
});

describe('applyQuery (mêmes règles que le serveur)', () => {
  it('sans filtre : tout, du plus récent au plus ancien', () => {
    expect(ids()).toEqual(['a4', 'a3', 'a2', 'a1']);
  });

  it('filtre par zone, type et statut, combinables', () => {
    expect(ids({ zoneId: 1 })).toEqual(['a2', 'a1']);
    expect(ids({ type: 'LEAK_SUSPECTED' })).toEqual(['a4', 'a1']);
    expect(ids({ status: 'OPEN' })).toEqual(['a3', 'a1']);
    expect(ids({ zoneId: 2, type: 'LEAK_SUSPECTED', status: 'RESOLVED' })).toEqual(['a4']);
  });

  it('filtre par période, bornes incluses', () => {
    expect(ids({ from: '2026-11-14T10:05:00Z', to: '2026-11-14T10:10:00Z' })).toEqual(['a3', 'a2']);
    expect(ids({ from: '2026-11-14T10:11:00Z' })).toEqual(['a4']);
  });

  it('trie par colonne dans les deux sens', () => {
    expect(ids({ sort: { field: 'detectedAt', direction: 'asc' } })).toEqual(['a1', 'a2', 'a3', 'a4']);
    expect(ids({ sort: { field: 'zoneId', direction: 'desc' } }).slice(0, 2).sort()).toEqual(['a3', 'a4']);
    expect(applyQuery(ALL, q({ sort: { field: 'type', direction: 'asc' } })).content[0].type).toBe('LEAK_SUSPECTED');
  });

  it('pagine et renseigne les totaux', () => {
    const page = applyQuery(ALL, q({ size: 3, page: 1 }));
    expect(page.content.map((a) => a.id)).toEqual(['a1']);
    expect(page).toMatchObject({ page: 1, size: 3, totalElements: 4, totalPages: 2 });
  });

  it('une liste vide donne une page vide valide', () => {
    expect(applyQuery([], q())).toEqual({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 1 });
  });
});

describe('recherche rapide', () => {
  it('ne tient compte ni de la casse ni des accents', () => {
    expect(normalize('Pompe en DÉFAUT')).toBe('pompe en defaut');
    expect(matchesSearch(ALL[1], 'DEFAUT', names)).toBe(true);
  });

  it('porte sur le type, la zone, le capteur, la gravité et le statut', () => {
    expect(matchesSearch(ALL[0], 'fuite', names)).toBe(true);
    expect(matchesSearch(ALL[0], 'nord', names)).toBe(true);
    expect(matchesSearch(ALL[0], 'capteur 1', names)).toBe(true);
    expect(matchesSearch(ALL[0], 'critique', names)).toBe(true);
    expect(matchesSearch(ALL[0], 'ouverte', names)).toBe(true);
    expect(matchesSearch(ALL[0], 'resolue', names)).toBe(false);
  });

  it('une recherche vide laisse tout passer', () => {
    expect(matchesSearch(ALL[0], '   ', names)).toBe(true);
  });
});

describe('incident associé', () => {
  const incident = (id: number, anomalyId: string | null): Incident =>
    ({ id, anomalyId, zoneId: 1, sensorId: null, type: 'LEAK_SUSPECTED', status: 'OPEN', priority: 'HIGH', description: null, assignee: null, measuredAt: null, createdAt: '', updatedAt: '' });

  it('retrouve l’incident par l’identifiant de l’anomalie et ignore ceux créés à la main', () => {
    const map = incidentsByAnomaly([incident(7, 'a1'), incident(8, null), incident(9, 'a3')]);
    expect(map.size).toBe(2);
    expect(map.get('a1')?.id).toBe(7);
    expect(map.get('a2')).toBe(undefined);
  });
});

describe('durées et dates', () => {
  it('calcule les secondes entre deux dates, nul si impossible', () => {
    expect(secondsBetween('2026-11-14T10:00:00Z', '2026-11-14T10:00:12Z')).toBe(12);
    expect(secondsBetween(null, '2026-11-14T10:00:12Z')).toBeNull();
    expect(secondsBetween('2026-11-14T10:00:12Z', '2026-11-14T10:00:00Z')).toBeNull();
    expect(secondsBetween('nimporte', 'quoi')).toBeNull();
  });

  it('formate les durées', () => {
    expect(formatDuration(null)).toBe('—');
    expect(formatDuration(45)).toBe('45 s');
    expect(formatDuration(185)).toBe('3 min 05 s');
    expect(formatDuration(3720)).toBe('1 h 02 min');
  });

  it('convertit un champ datetime-local en date ISO et inversement', () => {
    expect(fromLocalInput('')).toBeNull();
    expect(fromLocalInput('pas une date')).toBeNull();
    const iso = fromLocalInput('2026-11-14T10:30') as string;
    expect(iso.endsWith('Z')).toBe(true);
    expect(toLocalInput(iso)).toBe('2026-11-14T10:30');
    expect(toLocalInput(null)).toBe('');
  });
});
