import { Observable } from 'rxjs';
import { AuditPage, AuditQuery } from './audit.model';

/**
 * Source des données du journal d'audit (SCRUM-46, FR-24).
 * Classe abstraite : elle sert aussi de jeton d'injection Angular.
 * Deux implémentations : ApiAuditDataSource (vraie API) et MockAuditDataSource (données simulées).
 */
export abstract class AuditDataSource {
  /** Une page du journal, les plus récentes d'abord. */
  abstract search(query: AuditQuery): Observable<AuditPage>;
  /** Les types d'action présents dans le journal, pour le filtre. */
  abstract actions(): Observable<string[]>;
}
