import { SEVERITY_LABELS, STATUS_LABELS } from '../../shared/badges/badge-labels';
import { Incident } from '../incidents/incident.model';
import { Anomaly, AnomalyPage, AnomalyQuery, SortField, typeLabel } from './anomaly.model';

/** Paramètres de GET /api/detection/anomalies : seuls les filtres renseignés sont envoyés. */
export function toParams(q: AnomalyQuery): Record<string, string> {
  const params: Record<string, string> = {
    page: String(q.page),
    size: String(q.size),
    sort: `${q.sort.field},${q.sort.direction}`,
  };
  if (q.zoneId !== null) {
    params['zoneId'] = String(q.zoneId);
  }
  if (q.type) {
    params['type'] = q.type;
  }
  if (q.status) {
    params['status'] = q.status;
  }
  if (q.from) {
    params['from'] = q.from;
  }
  if (q.to) {
    params['to'] = q.to;
  }
  return params;
}

/** Clic sur un en-tête : même colonne, on inverse ; autre colonne, on trie (récent d'abord pour la date). */
export function nextSort(current: AnomalyQuery['sort'], field: SortField): AnomalyQuery['sort'] {
  if (current.field === field) {
    return { field, direction: current.direction === 'asc' ? 'desc' : 'asc' };
  }
  return { field, direction: field === 'detectedAt' ? 'desc' : 'asc' };
}

/**
 * Applique une requête à une liste en mémoire, avec les mêmes règles que le serveur
 * (filtres, tri avec départage, pagination). Sert à la source de données de démonstration.
 */
export function applyQuery(all: Anomaly[], q: AnomalyQuery): AnomalyPage {
  const from = q.from ? Date.parse(q.from) : null;
  const to = q.to ? Date.parse(q.to) : null;
  const filtered = all.filter((a) => {
    const detected = Date.parse(a.detectedAt);
    return (
      (q.zoneId === null || a.zoneId === q.zoneId) &&
      (!q.type || a.type === q.type) &&
      (!q.status || a.status === q.status) &&
      (from === null || detected >= from) &&
      (to === null || detected <= to)
    );
  });
  const factor = q.sort.direction === 'asc' ? 1 : -1;
  const sorted = [...filtered].sort((a, b) => {
    const x = a[q.sort.field];
    const y = b[q.sort.field];
    const result = typeof x === 'number' && typeof y === 'number' ? x - y : String(x).localeCompare(String(y));
    return result !== 0 ? result * factor : b.id.localeCompare(a.id);
  });
  const totalPages = Math.max(1, Math.ceil(sorted.length / q.size));
  const start = q.page * q.size;
  return {
    content: sorted.slice(start, start + q.size),
    page: q.page,
    size: q.size,
    totalElements: sorted.length,
    totalPages,
  };
}

/** Minuscules et sans accents : « FUITE » retrouve « Fuite probable ». */
export function normalize(text: string): string {
  return text.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
}

export interface NameLookup {
  zoneName(id: number): string;
  sensorName(id: number | null): string;
}

/** Recherche rapide sur la page affichée : identifiant, type, zone, capteur, gravité et statut. */
export function matchesSearch(a: Anomaly, text: string, names: NameLookup): boolean {
  const q = normalize(text.trim());
  if (q === '') {
    return true;
  }
  const haystack = normalize(
    [a.id, a.type, typeLabel(a.type), names.zoneName(a.zoneId), names.sensorName(a.sensorId), SEVERITY_LABELS[a.severity], STATUS_LABELS[a.status]].join(' '),
  );
  return haystack.includes(q);
}

/** Incident créé à partir de chaque anomalie : le service des incidents garde l'identifiant de l'anomalie. */
export function incidentsByAnomaly(incidents: Incident[]): Map<string, Incident> {
  const map = new Map<string, Incident>();
  for (const incident of incidents) {
    if (incident.anomalyId) {
      map.set(incident.anomalyId, incident);
    }
  }
  return map;
}

/** Secondes écoulées entre deux dates ISO ; nul si l'une manque ou si l'ordre est inversé. */
export function secondsBetween(fromIso: string | null, toIso: string | null): number | null {
  if (!fromIso || !toIso) {
    return null;
  }
  const seconds = Math.round((Date.parse(toIso) - Date.parse(fromIso)) / 1000);
  return Number.isNaN(seconds) || seconds < 0 ? null : seconds;
}

/** « 45 s », « 3 min 05 s », « 1 h 02 min ». */
export function formatDuration(seconds: number | null): string {
  if (seconds === null) {
    return '—';
  }
  if (seconds < 60) {
    return `${seconds} s`;
  }
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    return `${minutes} min ${String(seconds % 60).padStart(2, '0')} s`;
  }
  return `${Math.floor(minutes / 60)} h ${String(minutes % 60).padStart(2, '0')} min`;
}

/** Valeur d'un champ datetime-local (heure locale) à partir d'une date ISO. */
export function toLocalInput(iso: string | null): string {
  if (!iso) {
    return '';
  }
  const d = new Date(iso);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}

/** Date ISO (UTC) à partir d'un champ datetime-local ; nul si le champ est vide ou invalide. */
export function fromLocalInput(value: string): string | null {
  if (!value) {
    return null;
  }
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? null : d.toISOString();
}
