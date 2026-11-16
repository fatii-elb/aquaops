import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AnomalyDataSource } from './anomaly-data-source';
import { AnomalyPage, AnomalyQuery } from './anomaly.model';
import { toParams } from './anomaly-rules';

/** GET /api/detection/anomalies (detection-service, via la passerelle). Le jeton est ajouté par authInterceptor. */
@Injectable()
export class ApiAnomalyDataSource extends AnomalyDataSource {
  private http = inject(HttpClient);
  private readonly url = '/api/detection/anomalies';

  list(query: AnomalyQuery): Observable<AnomalyPage> {
    return this.http.get<AnomalyPage>(this.url, { params: new HttpParams({ fromObject: toParams(query) }) });
  }
}
