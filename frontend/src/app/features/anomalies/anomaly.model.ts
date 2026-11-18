import { AnomalyStatus, Severity } from '../../shared/badges/badge-labels';

/** Types d'anomalies produits par le service de détection. */
export const ANOMALY_TYPES = ['LEAK_SUSPECTED', 'SENSOR_SILENT', 'PUMP_FAULT', 'LOW_LEVEL'] as const;

export const ANOMALY_TYPE_LABELS: Record<string, string> = {
  LEAK_SUSPECTED: 'Fuite probable',
  SENSOR_SILENT: 'Capteur silencieux',
  PUMP_FAULT: 'Pompe en défaut',
  LOW_LEVEL: 'Niveau de réservoir bas',
};

/** Un type inconnu (nouveau type ajouté côté serveur) reste affiché tel quel. */
export function typeLabel(type: string): string {
  return ANOMALY_TYPE_LABELS[type] ?? type;
}

/** Anomalie renvoyée par GET /api/detection/anomalies (contrat : contracts/api/detection.openapi.yaml). */
export interface Anomaly {
  id: string;
  type: string;
  zoneId: number;
  sensorId: number | null;
  severity: Severity;
  status: AnomalyStatus;
  /** Date de la mesure d'origine, UTC ISO 8601. */
  measuredAt: string | null;
  detectedAt: string;
  resolvedAt: string | null;
}

/** Page de résultats, au même format que le journal d'audit. */
export interface AnomalyPage {
  content: Anomaly[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Colonnes triables côté serveur. */
export type SortField = 'detectedAt' | 'zoneId' | 'type' | 'status';

export interface AnomalySort {
  field: SortField;
  direction: 'asc' | 'desc';
}

export interface AnomalyQuery {
  zoneId: number | null;
  type: string | null;
  status: AnomalyStatus | null;
  /** Bornes de la date de détection, UTC ISO 8601. */
  from: string | null;
  to: string | null;
  /** Page à partir de 0. */
  page: number;
  size: number;
  sort: AnomalySort;
}

export const DEFAULT_QUERY: AnomalyQuery = {
  zoneId: null,
  type: null,
  status: null,
  from: null,
  to: null,
  page: 0,
  size: 20,
  sort: { field: 'detectedAt', direction: 'desc' },
};
