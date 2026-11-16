export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type AnomalyStatus = 'OPEN' | 'RESOLVED';

export const SEVERITY_LABELS: Record<Severity, string> = {
  LOW: 'Faible',
  MEDIUM: 'Moyenne',
  HIGH: 'Haute',
  CRITICAL: 'Critique',
};

export const SEVERITY_CLASSES: Record<Severity, string> = {
  LOW: 'bg-surface-container-high text-on-surface-variant',
  MEDIUM: 'bg-secondary-container text-on-secondary-container',
  HIGH: 'bg-amber-100 text-amber-900',
  CRITICAL: 'bg-error-container text-on-error-container',
};

export const STATUS_LABELS: Record<AnomalyStatus, string> = {
  OPEN: 'Ouverte',
  RESOLVED: 'Résolue',
};

export const STATUS_CLASSES: Record<AnomalyStatus, string> = {
  OPEN: 'bg-primary-fixed text-on-primary-fixed-variant',
  RESOLVED: 'bg-tertiary-fixed/50 text-on-tertiary-fixed-variant',
};
