const ACTIONS: Record<string, string> = {
  CREATE: 'Création',
  UPDATE: 'Modification',
  DELETE: 'Suppression',
  ASSIGN: 'Affectation',
  STATUS_CHANGE: 'Changement de statut',
  INCIDENT_CREATED: 'Incident créé',
  INCIDENT_CONFIRMED: 'Incident confirmé',
  INCIDENT_ASSIGNED: 'Incident affecté',
  INCIDENT_STATUS_CHANGED: "Statut de l'incident modifié",
  INCIDENT_RESOLVED: 'Incident résolu',
  INCIDENT_CLOSED: 'Incident clos',
  LOGIN: 'Connexion',
  LOGIN_ERROR: 'Échec de connexion',
  LOGOUT: 'Déconnexion',
};

const ENTITIES: Record<string, string> = {
  ZONE: 'Zone',
  PIPE: 'Conduite',
  TANK: 'Réservoir',
  PUMP: 'Pompe',
  SENSOR: 'Capteur',
  INTERVENTION: 'Intervention',
  INCIDENT: 'Incident',
  USER: 'Utilisateur',
};

/** Libellé français d'une action ; une valeur inconnue s'affiche telle quelle (jamais d'erreur). */
export function actionLabel(action: string): string {
  return ACTIONS[action] ?? action;
}

export function entityLabel(entityType: string): string {
  return ENTITIES[entityType] ?? entityType;
}

/**
 * Convertit deux jours saisis (AAAA-MM-JJ, heure locale) en instants ISO pour l'API :
 * le jour de début à 00:00:00, le jour de fin à 23:59:59.999, tous deux inclus.
 */
export function periodToIso(from: string, to: string): { from?: string; to?: string } {
  const period: { from?: string; to?: string } = {};
  if (from) period.from = new Date(`${from}T00:00:00`).toISOString();
  if (to) period.to = new Date(`${to}T23:59:59.999`).toISOString();
  return period;
}

/** Message clair pour l'utilisateur à partir d'une erreur HTTP. */
export function errorMessage(error: unknown): string {
  const status =
    typeof error === 'object' && error !== null && 'status' in error ? (error as { status: unknown }).status : undefined;
  switch (status) {
    case 403:
      return "Accès refusé (403) : le journal d'audit est réservé aux rôles superviseur et administrateur.";
    case 401:
      return 'Session expirée : reconnectez-vous.';
    case 400:
      return 'Filtres invalides : vérifiez les dates et la taille de page.';
    case 0:
      return 'Service injoignable : vérifiez la connexion puis réessayez.';
    default:
      return 'Impossible de charger le journal d\u2019audit. Réessayez dans un instant.';
  }
}
