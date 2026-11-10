import { HistoryAction, Incident, IncidentStatus, Priority } from './incident.model';

export const ALL_STATUSES: IncidentStatus[] = ['OPEN', 'CONFIRMED', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];

/** Même table que IncidentStateMachine côté serveur : le serveur reste seul juge, l'écran ne propose que le possible. */
const TRANSITIONS: Record<IncidentStatus, IncidentStatus[]> = {
  OPEN: ['CONFIRMED', 'CLOSED'],
  CONFIRMED: ['ASSIGNED', 'CLOSED'],
  ASSIGNED: ['IN_PROGRESS', 'CLOSED'],
  IN_PROGRESS: ['RESOLVED', 'CLOSED'],
  RESOLVED: ['CLOSED'],
  CLOSED: [],
};

/** Statuts que l'on peut atteindre depuis `status`. */
export function nextStatuses(status: IncidentStatus): IncidentStatus[] {
  return TRANSITIONS[status] ?? [];
}

const STATUS_LABEL: Record<IncidentStatus, string> = {
  OPEN: 'Ouvert',
  CONFIRMED: 'Confirmé',
  ASSIGNED: 'Affecté',
  IN_PROGRESS: 'En cours',
  RESOLVED: 'Résolu',
  CLOSED: 'Clos',
};

const STATUS_CLASS: Record<IncidentStatus, string> = {
  OPEN: 'bg-error-container text-on-error-container',
  CONFIRMED: 'bg-amber-100 text-amber-800',
  ASSIGNED: 'bg-primary-fixed text-on-primary-fixed',
  IN_PROGRESS: 'bg-secondary-container text-on-secondary-container',
  RESOLVED: 'bg-tertiary-fixed/40 text-tertiary',
  CLOSED: 'bg-surface-container-high text-on-surface-variant',
};

const PRIORITY_LABEL: Record<Priority, string> = {
  CRITICAL: 'Critique',
  HIGH: 'Élevée',
  MEDIUM: 'Moyenne',
  LOW: 'Faible',
};

const PRIORITY_CLASS: Record<Priority, string> = {
  CRITICAL: 'bg-error text-on-error',
  HIGH: 'bg-amber-500 text-surface-container-lowest',
  MEDIUM: 'bg-primary-fixed text-on-primary-fixed',
  LOW: 'bg-surface-container-high text-on-surface',
};

const PRIORITY_RANK: Record<Priority, number> = { CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1 };

const TYPE_LABEL: Record<string, string> = {
  LEAK_SUSPECTED: 'Fuite suspectée',
  PUMP_FAULT: 'Défaut de pompe',
  LOW_LEVEL: 'Niveau bas',
  SENSOR_SILENT: 'Capteur silencieux',
};

const ACTION_LABEL: Record<HistoryAction, string> = {
  CONFIRMED: "a confirmé l'incident",
  ASSIGNED: "a affecté l'incident",
  COMMENTED: 'a commenté',
  CLOSED: "a clos l'incident",
  STATUS_CHANGED: "a changé l'état",
};

/** Texte du bouton qui fait passer l'incident à ce statut. */
const BUTTON_LABEL: Record<IncidentStatus, string> = {
  OPEN: 'Rouvrir',
  CONFIRMED: 'Confirmer',
  ASSIGNED: 'Affecter',
  IN_PROGRESS: "Démarrer l'intervention",
  RESOLVED: 'Valider la résolution',
  CLOSED: 'Clore',
};

export const statusLabel = (s: IncidentStatus): string => STATUS_LABEL[s] ?? s;
export const statusClass = (s: IncidentStatus): string => STATUS_CLASS[s] ?? '';
export const priorityLabel = (p: Priority): string => PRIORITY_LABEL[p] ?? p;
export const priorityClass = (p: Priority): string => PRIORITY_CLASS[p] ?? '';
export const typeLabel = (type: string): string => TYPE_LABEL[type] ?? type;
export const actionLabel = (a: HistoryAction): string => ACTION_LABEL[a] ?? a;
export const buttonLabel = (s: IncidentStatus): string => BUTTON_LABEL[s] ?? s;

export function isClosed(status: IncidentStatus): boolean {
  return status === 'CLOSED';
}

/** Les incidents clos en dernier ; parmi les autres, priorité décroissante puis plus récent d'abord. */
export function sortIncidents(list: Incident[]): Incident[] {
  return [...list].sort((a, b) => {
    const closed = Number(isClosed(a.status)) - Number(isClosed(b.status));
    if (closed !== 0) {
      return closed;
    }
    const byPriority = PRIORITY_RANK[b.priority] - PRIORITY_RANK[a.priority];
    if (byPriority !== 0) {
      return byPriority;
    }
    return b.createdAt.localeCompare(a.createdAt);
  });
}

/** Date et heure lisibles (fuseau de l'utilisateur). */
export function formatDateTime(iso: string | null): string {
  if (!iso) {
    return '—';
  }
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) {
    return '—';
  }
  return (
    d.toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' }) +
    ' • ' +
    d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  );
}

export const PRIORITIES: Priority[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];

export const INCIDENT_TYPES = ['LEAK_SUSPECTED', 'PUMP_FAULT', 'LOW_LEVEL', 'SENSOR_SILENT'];

/** Les six étapes du cycle de vie, dans l'ordre, pour la barre de pipeline. */
export const STAGES: { status: IncidentStatus; title: string; hint: string; dot: string; bar: string }[] = [
  { status: 'OPEN', title: '1. Ouvert', hint: 'brut', dot: 'bg-error', bar: 'bg-error' },
  { status: 'CONFIRMED', title: '2. Confirmé', hint: 'qualifié', dot: 'bg-secondary', bar: 'bg-secondary' },
  { status: 'ASSIGNED', title: '3. Affecté', hint: 'équipe', dot: 'bg-primary', bar: 'bg-primary' },
  { status: 'IN_PROGRESS', title: '4. En cours', hint: 'terrain actif', dot: 'bg-primary', bar: 'bg-primary' },
  { status: 'RESOLVED', title: '5. Résolu', hint: 'à clore', dot: 'bg-tertiary', bar: 'bg-tertiary' },
  { status: 'CLOSED', title: '6. Clos', hint: 'archivés', dot: 'bg-outline', bar: 'bg-secondary-container' },
];

/** Code lisible d'un incident, ex. INC-2026-089 (année de création + identifiant sur 3 chiffres). */
export function incidentCode(incident: Pick<Incident, 'id' | 'createdAt'>): string {
  const year = new Date(incident.createdAt).getUTCFullYear();
  return `INC-${Number.isNaN(year) ? '----' : year}-${String(incident.id).padStart(3, '0')}`;
}

/** « il y a 22 min », « il y a 1 h 45 min », « il y a 3 j ». */
export function timeAgo(iso: string | null, nowMs: number = Date.now()): string {
  if (!iso) {
    return '—';
  }
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) {
    return '—';
  }
  const seconds = Math.max(0, Math.round((nowMs - then) / 1000));
  if (seconds < 60) {
    return "à l'instant";
  }
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    return `il y a ${minutes} min`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return `il y a ${hours} h ${String(minutes % 60).padStart(2, '0')} min`;
  }
  return `il y a ${Math.floor(hours / 24)} j`;
}

/** Délai entre la mesure d'origine et la création de l'incident, en secondes (NFR-02), ou null. */
export function delaySeconds(measuredAt: string | null, createdAt: string): number | null {
  if (!measuredAt) {
    return null;
  }
  const a = new Date(measuredAt).getTime();
  const b = new Date(createdAt).getTime();
  if (Number.isNaN(a) || Number.isNaN(b)) {
    return null;
  }
  return Math.max(0, Math.round((b - a) / 1000));
}

/** Heure seule (HH:MM:SS), pour la chronologie. */
export function formatTime(iso: string | null): string {
  if (!iso) {
    return '—';
  }
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}
