import { Observable } from 'rxjs';
import { AnomalyPage, AnomalyQuery } from './anomaly.model';

/** Origine des anomalies : l'API réelle ou des données de démonstration (voir anomaly-data-source.config.ts). */
export abstract class AnomalyDataSource {
  abstract list(query: AnomalyQuery): Observable<AnomalyPage>;
}
