import { describe, expect, it } from 'vitest';
import { Incident } from '../incidents/incident.model';
import { csvCell, computeIndicators, csvFileName, filterByPeriod, filterReport, formatDuration, incidentsPerDay, lineChart, periodRange, rangeError, toCsv } from './reports-logic';
import { sampleIncidents, sampleInterventions } from './reports-sample-data';

/** « Maintenant » du jeu de données : les résultats attendus ci-dessous sont comptés à la main. */
const NOW = new Date('2026-12-10T12:00:00Z');
const incidents = sampleIncidents(NOW);
const interventions = sampleInterventions(NOW);
const HOUR = 3_600_000;

describe('périodes', () => {
  it('7 jours : 5 incidents créés (les n° 1 à 5)', () => {
    const list = filterByPeriod(incidents, periodRange('7d', '', '', NOW));
    expect(list.map((i) => i.id).sort((a, b) => a - b)).toEqual([1, 2, 3, 4, 5]);
  });

  it('30 jours : 9 incidents ; tout : 12', () => {
    expect(filterByPeriod(incidents, periodRange('30d', '', '', NOW))).toHaveLength(9);
    expect(filterByPeriod(incidents, periodRange('all', '', '', NOW))).toHaveLength(12);
  });

  it('dates personnalisées : jours entiers, bornes comprises', () => {
    const list = filterByPeriod(incidents, periodRange('custom', '2026-12-02', '2026-12-05', NOW));
    expect(list.map((i) => i.id).sort((a, b) => a - b)).toEqual([5, 6]);
  });

  it('une seule borne personnalisée suffit', () => {
    expect(filterByPeriod(incidents, periodRange('custom', '2026-12-05', '', NOW))).toHaveLength(5);
  });

  it('début après la fin : message d\'erreur et aucun incident', () => {
    const range = periodRange('custom', '2026-12-08', '2026-12-01', NOW);
    expect(rangeError(range)).toBe('La date de début doit précéder la date de fin.');
    expect(filterByPeriod(incidents, range)).toEqual([]);
  });

  it('période valide : pas d\'erreur', () => {
    expect(rangeError(periodRange('30d', '', '', NOW))).toBeNull();
  });
});

describe('indicateurs', () => {
  it('7 jours : comptes, temps moyen de résolution et interventions', () => {
    const range = periodRange('7d', '', '', NOW);
    const result = computeIndicators(filterByPeriod(incidents, range), interventions, range);
    expect(result.total).toBe(5);
    expect(result.finished).toBe(3); // n° 1, 2, 5
    expect(result.active).toBe(2); // n° 3, 4
    expect(result.meanResolutionMs).toBe(4 * HOUR); // (4 h + 2 h + 6 h) / 3
    expect(result.interventionsDone).toBe(2);
    expect(result.byPriority.map((r) => [r.key, r.count])).toEqual([['CRITICAL', 1], ['HIGH', 2], ['MEDIUM', 1], ['LOW', 1]]);
    expect(result.byType.map((r) => [r.key, r.count])).toEqual([['LEAK_SUSPECTED', 2], ['PUMP_FAULT', 1], ['LOW_LEVEL', 1], ['SENSOR_SILENT', 1]]);
    expect(result.byZone.map((r) => [r.key, r.count])).toEqual([['1', 2], ['2', 2], ['3', 1]]);
  });

  it('30 jours : 9 incidents, 6 terminés, moyenne 3 h 30', () => {
    const range = periodRange('30d', '', '', NOW);
    const result = computeIndicators(filterByPeriod(incidents, range), interventions, range);
    expect(result.total).toBe(9);
    expect(result.finished).toBe(6);
    expect(result.meanResolutionMs).toBe(3.5 * HOUR); // 21 h / 6
    expect(result.interventionsDone).toBe(3);
  });

  it('changer de période change les indicateurs', () => {
    const at = (preset: '7d' | '30d') => {
      const range = periodRange(preset, '', '', NOW);
      return computeIndicators(filterByPeriod(incidents, range), interventions, range);
    };
    expect(at('7d').total).not.toBe(at('30d').total);
    expect(at('7d').meanResolutionMs).not.toBe(at('30d').meanResolutionMs);
  });

  it('les barres sont relatives à la plus grande valeur du groupe', () => {
    const range = periodRange('7d', '', '', NOW);
    const result = computeIndicators(filterByPeriod(incidents, range), interventions, range);
    expect(result.byPriority.find((r) => r.key === 'HIGH')?.percent).toBe(100);
    expect(result.byPriority.find((r) => r.key === 'LOW')?.percent).toBe(50);
  });

  it('aucun incident : zéros, pas de moyenne, 4 priorités quand même', () => {
    const range = periodRange('7d', '', '', NOW);
    const result = computeIndicators([], interventions, range);
    expect(result.total).toBe(0);
    expect(result.meanResolutionMs).toBeNull();
    expect(result.byPriority).toHaveLength(4);
    expect(result.byPriority.every((r) => r.count === 0 && r.percent === 0)).toBe(true);
    expect(result.byType).toEqual([]);
  });

  it('API des interventions indisponible : indicateur nul, le reste est calculé', () => {
    const range = periodRange('30d', '', '', NOW);
    const result = computeIndicators(filterByPeriod(incidents, range), null, range);
    expect(result.interventionsDone).toBeNull();
    expect(result.total).toBe(9);
  });
});

describe('durées', () => {
  it('formate en jours, heures et minutes', () => {
    expect(formatDuration(null)).toBe('—');
    expect(formatDuration(30 * 1000)).toBe('moins d\u2019une minute');
    expect(formatDuration(45 * 60_000)).toBe('45 min');
    expect(formatDuration(2 * HOUR)).toBe('2 h');
    expect(formatDuration(2 * HOUR + 15 * 60_000)).toBe('2 h 15 min');
    expect(formatDuration(28 * HOUR)).toBe('1 j 4 h');
  });
});

describe('courbe', () => {
  it('compte par jour, jours vides compris', () => {
    const days = incidentsPerDay(filterByPeriod(incidents, periodRange('7d', '', '', NOW)));
    expect(days.map((d) => d.day)).toEqual(['2026-12-05', '2026-12-06', '2026-12-07', '2026-12-08', '2026-12-09']);
    expect(days.map((d) => d.count)).toEqual([1, 1, 1, 1, 1]);
  });

  it('un jour vide entre deux incidents donne 0', () => {
    const list = filterByPeriod(incidents, periodRange('custom', '2026-12-01', '2026-12-05', NOW)); // n° 5 (5 déc.) et n° 6 (2 déc.)
    expect(incidentsPerDay(list).map((d) => d.count)).toEqual([1, 0, 0, 1]);
  });

  it('aucun incident : pas de courbe', () => {
    expect(incidentsPerDay([])).toEqual([]);
    expect(lineChart([]).points).toBe('');
  });

  it('les points restent dans le cadre', () => {
    const chart = lineChart([{ day: '2026-12-01', count: 0 }, { day: '2026-12-02', count: 3 }]);
    expect(chart.max).toBe(3);
    expect(chart.dots[0].y).toBeGreaterThan(chart.dots[1].y);
    expect(chart.dots.every((d) => d.x >= 0 && d.x <= 600 && d.y >= 0 && d.y <= 160)).toBe(true);
  });
});

describe('rapport filtré', () => {
  const none = { priority: '' as const, status: '' as const, zoneId: null };

  it('sans filtre : tout, les plus récents d\'abord', () => {
    expect(filterReport(incidents, none).map((i) => i.id).slice(0, 3)).toEqual([1, 2, 3]);
  });

  it('les filtres se combinent', () => {
    expect(filterReport(incidents, { ...none, priority: 'CRITICAL' }).map((i) => i.id)).toEqual([1, 6, 11]);
    expect(filterReport(incidents, { ...none, priority: 'CRITICAL', zoneId: 1 }).map((i) => i.id)).toEqual([1, 6]);
    expect(filterReport(incidents, { priority: 'CRITICAL', status: 'OPEN', zoneId: 1 })).toEqual([]);
  });
});

describe('export CSV', () => {
  const labels = {
    zone: (id: number) => `Zone ${id}`,
    type: (t: string) => t,
    priority: (p: string) => p,
    status: (s: string) => s,
  };

  it('une ligne d\'en-tête et exactement une ligne par incident', () => {
    const lines = filterReport(incidents, { priority: 'CRITICAL', status: '', zoneId: null });
    const rows = toCsv(lines, labels as never).trimEnd().split('\r\n');
    expect(rows).toHaveLength(1 + lines.length);
    expect(rows[0]).toBe('Code;Créé le (UTC);Zone;Type;Priorité;État;Responsable;Description;Mis à jour le (UTC)');
    expect(rows[1].startsWith('INC-2026-001;')).toBe(true);
  });

  it('protège les séparateurs, les guillemets et les formules', () => {
    expect(csvCell('a;b')).toBe('"a;b"');
    expect(csvCell('dit "oui"')).toBe('"dit ""oui"""');
    expect(csvCell('ligne1\nligne2')).toBe('"ligne1\nligne2"');
    expect(csvCell('=SOMME(A1:A2)')).toBe("'=SOMME(A1:A2)");
    expect(csvCell('+33612345678')).toBe("'+33612345678");
    expect(csvCell(null)).toBe('');
    expect(csvCell(7)).toBe('7');
  });

  it('une description avec « ; » reste dans une seule cellule', () => {
    const one: Incident = { ...incidents[0], description: 'fuite; pression basse' };
    const row = toCsv([one], labels as never).trimEnd().split('\r\n')[1];
    expect(row).toContain('"fuite; pression basse"');
  });

  it('nom du fichier daté', () => {
    expect(csvFileName(NOW)).toBe('rapport-incidents-2026-12-10.csv');
  });
});
