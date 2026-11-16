import { Incident, IncidentStatus, Priority, ZoneInfo } from '../incidents/incident.model';
import { Intervention, InterventionStatus } from '../interventions/intervention.model';

/**
 * Jeu de données connu pour la page Rapports (mock et tests, FE-10 : « jeu de données de test dans le dépôt »).
 * Les dates sont décalées à partir d'une date de référence : `base` = « maintenant ».
 */

interface Row {
  id: number;
  daysAgo: number;
  type: string;
  priority: Priority;
  status: IncidentStatus;
  zoneId: number;
  /** Durée de traitement en heures (incidents résolus ou clos). */
  hours?: number;
  assignee?: string;
}

const ROWS: Row[] = [
  { id: 1, daysAgo: 1, type: 'LEAK_SUSPECTED', priority: 'CRITICAL', status: 'CLOSED', zoneId: 1, hours: 4, assignee: 'tech1' },
  { id: 2, daysAgo: 2, type: 'LEAK_SUSPECTED', priority: 'HIGH', status: 'RESOLVED', zoneId: 1, hours: 2, assignee: 'tech1' },
  { id: 3, daysAgo: 3, type: 'PUMP_FAULT', priority: 'HIGH', status: 'IN_PROGRESS', zoneId: 2, assignee: 'tech2' },
  { id: 4, daysAgo: 4, type: 'LOW_LEVEL', priority: 'MEDIUM', status: 'OPEN', zoneId: 2 },
  { id: 5, daysAgo: 5, type: 'SENSOR_SILENT', priority: 'LOW', status: 'CLOSED', zoneId: 3, hours: 6 },
  { id: 6, daysAgo: 8, type: 'LEAK_SUSPECTED', priority: 'CRITICAL', status: 'CLOSED', zoneId: 1, hours: 3, assignee: 'tech1' },
  { id: 7, daysAgo: 10, type: 'PUMP_FAULT', priority: 'MEDIUM', status: 'ASSIGNED', zoneId: 3, assignee: 'tech2' },
  { id: 8, daysAgo: 12, type: 'LOW_LEVEL', priority: 'LOW', status: 'RESOLVED', zoneId: 2, hours: 1 },
  { id: 9, daysAgo: 20, type: 'SENSOR_SILENT', priority: 'LOW', status: 'CLOSED', zoneId: 3, hours: 5 },
  { id: 10, daysAgo: 35, type: 'LEAK_SUSPECTED', priority: 'HIGH', status: 'CLOSED', zoneId: 1, hours: 8, assignee: 'tech1' },
  { id: 11, daysAgo: 60, type: 'PUMP_FAULT', priority: 'CRITICAL', status: 'CLOSED', zoneId: 2, hours: 10, assignee: 'tech2' },
  { id: 12, daysAgo: 100, type: 'LOW_LEVEL', priority: 'MEDIUM', status: 'CLOSED', zoneId: 3, hours: 12 },
];

const HOUR_MS = 3_600_000;
const DAY_MS = 24 * HOUR_MS;

export function sampleIncidents(base: Date): Incident[] {
  return ROWS.map((row) => {
    const created = new Date(base.getTime() - row.daysAgo * DAY_MS);
    const updated = new Date(created.getTime() + (row.hours ?? 0) * HOUR_MS);
    return {
      id: row.id,
      anomalyId: null,
      zoneId: row.zoneId,
      sensorId: `S${String(row.id).padStart(2, '0')}`,
      type: row.type,
      status: row.status,
      priority: row.priority,
      description: `Incident de démonstration n° ${row.id}`,
      assignee: row.assignee ?? null,
      measuredAt: null,
      createdAt: created.toISOString(),
      updatedAt: updated.toISOString(),
    };
  });
}

const INTERVENTIONS: { id: number; incidentId: number; daysAgo: number; status: InterventionStatus }[] = [
  { id: 1, incidentId: 1, daysAgo: 1, status: 'PLANNED' },
  { id: 2, incidentId: 2, daysAgo: 2, status: 'DONE' },
  { id: 3, incidentId: 3, daysAgo: 3, status: 'IN_PROGRESS' },
  { id: 4, incidentId: 6, daysAgo: 6, status: 'DONE' },
  { id: 5, incidentId: 8, daysAgo: 15, status: 'DONE' },
  { id: 6, incidentId: 10, daysAgo: 40, status: 'DONE' },
];

export function sampleInterventions(base: Date): Intervention[] {
  return INTERVENTIONS.map((row) => ({
    id: row.id,
    incidentId: row.incidentId,
    team: 'Équipe A',
    assignedTo: null,
    status: row.status,
    createdAt: new Date(base.getTime() - row.daysAgo * DAY_MS).toISOString(),
  }));
}

export const SAMPLE_ZONES: ZoneInfo[] = [
  { id: 1, name: 'Centre-ville' },
  { id: 2, name: 'Zone industrielle' },
  { id: 3, name: 'Quartier nord' },
];
