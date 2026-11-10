/**
 * Choix de la source de données du journal d'audit (FR-24).
 *   'api'  : service des actifs, GET /api/assets/audit (valeur par défaut)
 *   'mock' : données simulées, aucun service nécessaire
 * Pour changer : modifier `mode` ci-dessous. Rien d'autre à toucher.
 */
export const AUDIT_DATA_SOURCE_CONFIG = {
  mode: 'api' as 'mock' | 'api',
  /**
   * Chemin relatif : aucune adresse n'est écrite en dur dans le build.
   * En développement, proxy.conf.json l'envoie vers la passerelle ; dans le conteneur,
   * nginx l'envoie vers la passerelle (variable d'environnement de l'image).
   */
  baseUrl: '/api/assets/audit',
  pageSize: 20,
};
