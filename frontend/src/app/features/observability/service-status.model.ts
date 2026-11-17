/** État d'un service : UP (répond), DOWN (ne répond pas), UNKNOWN (pas encore contrôlé). */
export type ServiceState = 'UP' | 'DOWN' | 'UNKNOWN';

/** Les 6 services listés par le plan (FE-11), dans l'ordre d'affichage. */
export const SERVICE_LABELS = {
  telemetry: 'Télémétrie',
  assets: 'Actifs',
  detection: 'Détection',
  incidents: 'Incidents',
  notifications: 'Notifications',
  gateway: 'Passerelle',
} as const;

/** Clé d'un service : 'telemetry' | 'assets' | ... */
export type ServiceKey = keyof typeof SERVICE_LABELS;

/** État d'un service tel qu'affiché sur la page Observabilité (FE-11). */
export interface ServiceStatus {
  /** Identifiant technique. */
  key: ServiceKey;
  /** Nom affiché. */
  label: string;
  state: ServiceState;
  /** Heure du dernier contrôle, au format ISO 8601. */
  checkedAt: string;
}