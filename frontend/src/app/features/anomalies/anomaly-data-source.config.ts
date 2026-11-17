/** Réglages de l'écran des anomalies. */
export const ANOMALY_DATA_SOURCE_CONFIG = {
  /** 'api' : GET /api/detection/anomalies via la passerelle ; 'mock' : données de démonstration sans backend. */
  mode: 'api' as 'api' | 'mock',
  /** Rafraîchissement automatique de la liste (FE-9 : toutes les 10 secondes). */
  refreshMs: 10_000,
  defaultPageSize: 20,
};
