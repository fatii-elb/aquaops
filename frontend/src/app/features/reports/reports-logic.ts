import { Incident, IncidentStatus, Priority } from '../incidents/incident.model';
import { INCIDENT_TYPES, PRIORITIES, incidentCode } from '../incidents/incident-rules';
import { Intervention } from '../interventions/intervention.model';

/**
 * Calculs de la page « Rapports & Analyses » (FE-10, FE-13) : fonctions pures, sans Angular ni réseau.
 * Les jours sont comptés en UTC, comme les dates renvoyées par l'API.
 */

export type PeriodPreset = '7d' | '30d' | '90d' | 'all' | 'custom';

export const PERIOD_PRESETS: { value: PeriodPreset; label: string }[] = [
  { value: '7d', label: '7 derniers jours' },
  { value: '30d', label: '30 derniers jours' },
  { value: '90d', label: '90 derniers jours' },
  { value: 'all', label: 'Tout' },
  { value: 'custom', label: 'Personnalisée' },
];

/** Bornes incluses. `null` = pas de borne. */
export interface DateRange {
  from: Date | null;
  to: Date | null;
}

const DAY_MS = 24 * 60 * 60 * 1000;

/** Période choisie -> bornes. Dates personnalisées au format AAAA-MM-JJ (jour entier, UTC). */
export function periodRange(preset: PeriodPreset, customFrom: string, customTo: string, now: Date): DateRange {
  switch (preset) {
    case '7d':
    case '30d':
    case '90d': {
      const days = Number.parseInt(preset, 10);
      return { from: new Date(now.getTime() - days * DAY_MS), to: now };
    }
    case 'custom':
      return {
        from: customFrom ? new Date(`${customFrom}T00:00:00.000Z`) : null,
        to: customTo ? new Date(`${customTo}T23:59:59.999Z`) : null,
      };
    default:
      return { from: null, to: null };
  }
}

/** Message si la période personnalisée est incohérente, sinon null. */
export function rangeError(range: DateRange): string | null {
  const invalid = (d: Date | null) => d !== null && Number.isNaN(d.getTime());
  if (invalid(range.from) || invalid(range.to)) return 'Date invalide.';
  if (range.from && range.to && range.from.getTime() > range.to.getTime()) {
    return 'La date de début doit précéder la date de fin.';
  }
  return null;
}

/** Incidents créés dans la période. */
export function filterByPeriod(incidents: Incident[], range: DateRange): Incident[] {
  if (rangeError(range)) return [];
  return incidents.filter((incident) => {
    const created = new Date(incident.createdAt).getTime();
    if (Number.isNaN(created)) return false;
    if (range.from && created < range.from.getTime()) return false;
    if (range.to && created > range.to.getTime()) return false;
    return true;
  });
}

/** Une ligne d'un graphique en barres : `percent` est relatif à la plus grande valeur du groupe. */
export interface CountRow {
  key: string;
  count: number;
  percent: number;
}

function toRows(counts: Map<string, number>, order?: string[]): CountRow[] {
  const keys = order ? [...order] : [...counts.keys()];
  for (const key of counts.keys()) if (!keys.includes(key)) keys.push(key);
  const rows = keys.map((key) => ({ key, count: counts.get(key) ?? 0, percent: 0 }));
  if (!order) rows.sort((a, b) => b.count - a.count || a.key.localeCompare(b.key));
  const max = Math.max(0, ...rows.map((r) => r.count));
  return rows.map((r) => ({ ...r, percent: max === 0 ? 0 : Math.round((r.count / max) * 100) }));
}

function countBy<T>(items: T[], key: (item: T) => string): Map<string, number> {
  const counts = new Map<string, number>();
  for (const item of items) counts.set(key(item), (counts.get(key(item)) ?? 0) + 1);
  return counts;
}

export interface Indicators {
  total: number;
  /** Incidents encore ouverts (ni résolus ni clos). */
  active: number;
  /** Incidents résolus ou clos. */
  finished: number;
  /** Durée moyenne création -> dernière mise à jour des incidents résolus ou clos ; null s'il n'y en a aucun. */
  meanResolutionMs: number | null;
  /** Interventions terminées créées dans la période ; null si l'API des interventions ne répond pas. */
  interventionsDone: number | null;
  byPriority: CountRow[];
  byType: CountRow[];
  /** `key` = identifiant de la zone, en texte. */
  byZone: CountRow[];
}

const FINISHED: IncidentStatus[] = ['RESOLVED', 'CLOSED'];

/**
 * Indicateurs de la période. `incidents` est déjà filtré par période.
 * Le temps de résolution est une approximation : l'API ne donne pas la date de résolution,
 * on prend la dernière mise à jour (champ updatedAt) des incidents résolus ou clos.
 */
export function computeIndicators(incidents: Incident[], interventions: Intervention[] | null, range: DateRange): Indicators {
  const finished = incidents.filter((i) => FINISHED.includes(i.status));
  const durations = finished
    .map((i) => new Date(i.updatedAt).getTime() - new Date(i.createdAt).getTime())
    .filter((ms) => Number.isFinite(ms) && ms >= 0);
  const done =
    interventions === null
      ? null
      : filterInterventionsByPeriod(interventions, range).filter((i) => i.status === 'DONE').length;
  return {
    total: incidents.length,
    active: incidents.length - finished.length,
    finished: finished.length,
    meanResolutionMs: durations.length === 0 ? null : Math.round(durations.reduce((a, b) => a + b, 0) / durations.length),
    interventionsDone: done,
    byPriority: toRows(countBy(incidents, (i) => i.priority), PRIORITIES),
    byType: toRows(countBy(incidents, (i) => i.type), INCIDENT_TYPES.filter((t) => incidents.some((i) => i.type === t))),
    byZone: toRows(countBy(incidents, (i) => String(i.zoneId))),
  };
}

function filterInterventionsByPeriod(interventions: Intervention[], range: DateRange): Intervention[] {
  if (rangeError(range)) return [];
  return interventions.filter((item) => {
    const created = new Date(item.createdAt).getTime();
    if (Number.isNaN(created)) return false;
    if (range.from && created < range.from.getTime()) return false;
    if (range.to && created > range.to.getTime()) return false;
    return true;
  });
}

/** « 2 h 15 min », « 45 min », « 3 j 4 h », « — » si inconnu. */
export function formatDuration(ms: number | null): string {
  if (ms === null || !Number.isFinite(ms) || ms < 0) return '—';
  if (ms < 60000) return 'moins d\u2019une minute';
  const minutes = Math.round(ms / 60000);
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return minutes % 60 === 0 ? `${hours} h` : `${hours} h ${minutes % 60} min`;
  const days = Math.floor(hours / 24);
  return hours % 24 === 0 ? `${days} j` : `${days} j ${hours % 24} h`;
}

// ----- Courbe : incidents par jour -----

export interface DayCount {
  day: string; // AAAA-MM-JJ (UTC)
  count: number;
}

const dayOf = (iso: string): string => new Date(iso).toISOString().slice(0, 10);

/** Nombre d'incidents créés chaque jour, du premier au dernier jour (jours vides compris). Vide s'il n'y a aucun incident. */
export function incidentsPerDay(incidents: Incident[]): DayCount[] {
  const valid = incidents.filter((i) => !Number.isNaN(new Date(i.createdAt).getTime()));
  if (valid.length === 0) return [];
  const counts = countBy(valid, (i) => dayOf(i.createdAt));
  const days = [...counts.keys()].sort();
  const result: DayCount[] = [];
  for (let t = Date.parse(days[0]); t <= Date.parse(days[days.length - 1]); t += DAY_MS) {
    const day = new Date(t).toISOString().slice(0, 10);
    result.push({ day, count: counts.get(day) ?? 0 });
  }
  return result;
}

export interface LineChart {
  points: string;
  dots: { x: number; y: number; day: string; count: number }[];
  max: number;
}

export const CHART_WIDTH = 600;
export const CHART_HEIGHT = 160;

/** Coordonnées SVG de la courbe (cadre de CHART_WIDTH x CHART_HEIGHT avec marges). */
export function lineChart(days: DayCount[]): LineChart {
  const max = Math.max(1, ...days.map((d) => d.count));
  const left = 16;
  const right = CHART_WIDTH - 16;
  const top = 12;
  const bottom = CHART_HEIGHT - 12;
  const dots = days.map((d, index) => ({
    x: days.length === 1 ? (left + right) / 2 : Math.round(left + (index / (days.length - 1)) * (right - left)),
    y: Math.round(bottom - (d.count / max) * (bottom - top)),
    day: d.day,
    count: d.count,
  }));
  return { points: dots.map((d) => `${d.x},${d.y}`).join(' '), dots, max };
}

// ----- Rapport filtrable et export CSV (FE-13) -----

export interface ReportFilters {
  priority: Priority | '';
  status: IncidentStatus | '';
  zoneId: number | null;
}

/** Lignes du rapport : filtres combinés, les plus récentes d'abord. */
export function filterReport(incidents: Incident[], filters: ReportFilters): Incident[] {
  return incidents
    .filter((i) => !filters.priority || i.priority === filters.priority)
    .filter((i) => !filters.status || i.status === filters.status)
    .filter((i) => filters.zoneId === null || i.zoneId === filters.zoneId)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id);
}

export const CSV_HEADER = ['Code', 'Créé le (UTC)', 'Zone', 'Type', 'Priorité', 'État', 'Responsable', 'Description', 'Mis à jour le (UTC)'];

/** Protège une cellule : guillemets doublés, et pas de formule si le texte commence par = + - @. */
export function csvCell(value: string | number | null | undefined): string {
  let text = value === null || value === undefined ? '' : String(value);
  if (/^[=+\-@\t\r]/.test(text)) text = `'${text}`;
  return /[";\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

export interface CsvLabels {
  zone: (zoneId: number) => string;
  type: (type: string) => string;
  priority: (priority: Priority) => string;
  status: (status: IncidentStatus) => string;
}

/** Contenu CSV (séparateur « ; » pour Excel en français, lignes terminées par CRLF). Une ligne par incident donné. */
export function toCsv(incidents: Incident[], labels: CsvLabels): string {
  const lines = [CSV_HEADER.map(csvCell).join(';')];
  for (const i of incidents) {
    lines.push(
      [
        incidentCode(i),
        i.createdAt,
        labels.zone(i.zoneId),
        labels.type(i.type),
        labels.priority(i.priority),
        labels.status(i.status),
        i.assignee,
        i.description,
        i.updatedAt,
      ]
        .map(csvCell)
        .join(';'),
    );
  }
  return lines.join('\r\n') + '\r\n';
}

export function csvFileName(now: Date): string {
  return `rapport-incidents-${now.toISOString().slice(0, 10)}.csv`;
}
