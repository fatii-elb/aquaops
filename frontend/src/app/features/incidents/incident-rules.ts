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
  ASSIGNED: 'Assigné',
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
  HIGH: 'Haute',
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
  ASSIGNED: "a assigné l'incident",
  COMMENTED: 'a commenté',
  CLOSED: "a clos l'incident",
  STATUS_CHANGED: "a changé l'état",
};

/** Texte du bouton qui fait passer l'incident à ce statut. */
const BUTTON_LABEL: Record<IncidentStatus, string> = {
  OPEN: 'Rouvrir',
  CONFIRMED: 'Confirmer',
  ASSIGNED: 'Assigner',
  IN_PROGRESS: "Démarrer l'intervention",
  RESOLVED: 'Marquer résolu',
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
