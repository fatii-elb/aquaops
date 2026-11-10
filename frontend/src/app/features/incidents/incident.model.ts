export type IncidentStatus = 'OPEN' | 'CONFIRMED' | 'ASSIGNED' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';

export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

/** Incident renvoyé par GET /api/incidents (service des incidents). */
export interface Incident {
  id: number;
  anomalyId: string | null;
  zoneId: number;
  sensorId: string | null;
  type: string;
  status: IncidentStatus;
  priority: Priority;
  description: string | null;
  assignee: string | null;
  /** Mesure d'origine, UTC, ISO 8601. */
  measuredAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export type HistoryAction = 'CONFIRMED' | 'ASSIGNED' | 'COMMENTED' | 'CLOSED' | 'STATUS_CHANGED';

/** Ligne d'historique renvoyée par GET /api/incidents/{id}/history (FR-16). */
export interface HistoryEntry {
  id: number;
  incidentId: number;
  action: HistoryAction;
  fromStatus: IncidentStatus | null;
  toStatus: IncidentStatus | null;
  author: string | null;
  comment: string | null;
  occurredAt: string;
}

/** Corps de PUT /api/incidents/{id}. */
export interface UpdateIncidentBody {
  status: IncidentStatus;
  assignee: string | null;
  actor: string | null;
}

/** Corps de POST /api/incidents (incident déclaré manuellement). */
export interface CreateIncidentBody {
  zoneId: number;
  type: string;
  priority: Priority;
  description: string | null;
  actor: string | null;
}

export interface ZoneInfo {
  id: number;
  name: string;
}
