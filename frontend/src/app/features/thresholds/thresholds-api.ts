import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ThresholdValues, ThresholdsResponse, ZoneThresholdView } from './thresholds.model';

/** Seuils par zone du detection-service (FR-13), via la passerelle. Le jeton est ajouté par authInterceptor. */
@Injectable({ providedIn: 'root' })
export class ThresholdApi {
  private http = inject(HttpClient);
  private readonly url = '/api/detection/thresholds';

  list(): Observable<ThresholdsResponse> {
    return this.http.get<ThresholdsResponse>(this.url);
  }

  /** Remplace les seuils de la zone (administrateur uniquement, vérifié par le serveur). */
  save(zoneId: number, values: ThresholdValues): Observable<ZoneThresholdView> {
    return this.http.put<ZoneThresholdView>(`${this.url}/${zoneId}`, values);
  }
}
