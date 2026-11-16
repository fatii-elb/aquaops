import { Observable } from 'rxjs';
import { Incident, ZoneInfo } from '../incidents/incident.model';
import { Intervention } from '../interventions/intervention.model';

/**
 * Source des données de la page Rapports & Analyses (FE-10, FE-13).
 * Classe abstraite : elle sert aussi de jeton d'injection Angular.
 * Deux implémentations : ApiReportsDataSource (vraies API) et MockReportsDataSource (données simulées).
 */
export abstract class ReportsDataSource {
  /** Tous les incidents. Une erreur est transmise à l'écran. */
  abstract incidents(): Observable<Incident[]>;
  /** Les interventions, ou null si l'API ne répond pas : l'indicateur affiche alors « — ». */
  abstract interventions(): Observable<Intervention[] | null>;
  /** Les zones, pour afficher leur nom ; liste vide si l'API ne répond pas. */
  abstract zones(): Observable<ZoneInfo[]>;
}

/**
 * Choix de la source : 'api' (valeur par défaut) ou 'mock' (données simulées, aucun service nécessaire).
 * Pour changer : modifier `mode` ci-dessous. Rien d'autre à toucher.
 */
export const REPORTS_DATA_SOURCE_CONFIG = {
  mode: 'api' as 'mock' | 'api',
  pageSize: 20,
};
