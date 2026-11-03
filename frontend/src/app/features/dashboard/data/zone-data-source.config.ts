/**
 * Choix de la source de données du tableau de bord (SCRUM-29, FR-07).
 *   'mock' : données simulées, aucun service nécessaire (valeur par défaut)
 *   'api'  : vraies données (service des actifs + service de télémétrie)
 * Pour changer : modifier `mode` ci-dessous. Rien d'autre à toucher.
 */
export const ZONE_DATA_SOURCE_CONFIG = {
  mode: 'mock' as 'mock' | 'api',
  /** Chemins relatifs : en développement, `proxy.conf.json` les redirige vers les services. */
  assetsBaseUrl: '/api/assets',
  telemetryBaseUrl: '/api/telemetry',
  /** On cherche la dernière mesure de chaque capteur dans cette fenêtre de temps. */
  lookbackSeconds: 120,
};
