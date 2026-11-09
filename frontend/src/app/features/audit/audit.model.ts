/** Une ligne du journal d'audit : qui, quand, quoi (FR-24). Format de GET /api/assets/audit. */
export interface AuditEntry {
  id: number;
  /** Quand : date ISO 8601, UTC. */
  occurredAt: string;
  /** Qui : nom d'utilisateur Keycloak, ou « system ». */
  actor: string;
  actorRole: string | null;
  /** Quoi : CREATE, UPDATE, DELETE, ASSIGN, STATUS_CHANGE, ou un type d'événement d'incident. */
  action: string;
  /** Objet touché : ZONE, PIPE, TANK, PUMP, SENSOR, INTERVENTION, INCIDENT. */
  entityType: string;
  entityId: string | null;
  details: string | null;
  source: string;
}

/** Filtres et pagination. Les dates sont des instants ISO 8601 (voir periodToIso). */
export interface AuditQuery {
  actor?: string;
  action?: string;
  from?: string;
  to?: string;
  /** Numéro de page, à partir de 0. */
  page: number;
  size: number;
}

export interface AuditPage {
  content: AuditEntry[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
