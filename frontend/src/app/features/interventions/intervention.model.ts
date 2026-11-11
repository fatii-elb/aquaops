export type InterventionStatus = 'PLANNED' | 'IN_PROGRESS' | 'DONE';

export interface Intervention {
  id: number;
  incidentId: number;
  team: string | null;
  assignedTo: string | null;
  status: InterventionStatus;
  createdAt: string;
}

export interface AssignInterventionBody {
  team: string;
  assignedTo?: string | null;
}

export const INTERVENTION_STATUS_LABELS: Record<InterventionStatus, string> = {
  PLANNED: 'Planifiée',
  IN_PROGRESS: 'En cours',
  DONE: 'Terminée',
};

/** Seule transition permise : Planifiée -> En cours -> Terminée (pas de retour, pas de saut). */
export function nextStatus(status: InterventionStatus): InterventionStatus | null {
  switch (status) {
    case 'PLANNED':
      return 'IN_PROGRESS';
    case 'IN_PROGRESS':
      return 'DONE';
    default:
      return null;
  }
}