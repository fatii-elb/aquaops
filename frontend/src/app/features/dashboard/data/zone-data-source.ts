import { Observable } from 'rxjs';
import { ZoneSummary } from './zone.model';

/**
 * Source des données du tableau de bord.
 * Classe abstraite : elle sert aussi de jeton d'injection Angular.
 * Deux implémentations : MockZoneDataSource (données simulées) et, plus tard, ApiZoneDataSource.
 */
export abstract class ZoneDataSource {
  /** Renvoie l'état courant de toutes les zones. Appelé toutes les REFRESH_MS. */
  abstract getZones(): Observable<ZoneSummary[]>;
}
