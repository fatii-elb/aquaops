import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AssignInterventionBody, Intervention, InterventionStatus } from './intervention.model';

/** Appels vers l'API des interventions (assets-service), toujours via la passerelle. */
@Injectable({ providedIn: 'root' })
export class InterventionApi {
  private http = inject(HttpClient);
  private readonly base = '/api/assets/interventions';

  list(incidentId?: number): Observable<Intervention[]> {
    const params = incidentId != null ? new HttpParams().set('incidentId', incidentId) : undefined;
    return this.http.get<Intervention[]>(this.base, { params });
  }

  create(incidentId: number): Observable<Intervention> {
    return this.http.post<Intervention>(this.base, { incidentId });
  }

  assign(id: number, body: AssignInterventionBody): Observable<Intervention> {
    return this.http.put<Intervention>(`${this.base}/${id}/assign`, body);
  }

  changeStatus(id: number, status: InterventionStatus): Observable<Intervention> {
    return this.http.put<Intervention>(`${this.base}/${id}/status`, { status });
  }
}