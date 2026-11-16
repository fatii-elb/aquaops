import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of } from 'rxjs';
import { IncidentApi } from '../incidents/incident-api';
import { Incident, ZoneInfo } from '../incidents/incident.model';
import { Intervention } from '../interventions/intervention.model';
import { InterventionApi } from '../interventions/intervention-api';
import { ReportsDataSource } from './reports-data-source';

/** Vraies données, via la passerelle : /api/incidents, /api/assets/interventions et /api/assets/zones. */
@Injectable()
export class ApiReportsDataSource extends ReportsDataSource {
  private incidentApi = inject(IncidentApi);
  private interventionApi = inject(InterventionApi);
  private http = inject(HttpClient);

  incidents(): Observable<Incident[]> {
    return this.incidentApi.list();
  }

  interventions(): Observable<Intervention[] | null> {
    return this.interventionApi.list().pipe(catchError(() => of(null)));
  }

  zones(): Observable<ZoneInfo[]> {
    return this.http.get<ZoneInfo[] | { content?: ZoneInfo[] }>('/api/assets/zones').pipe(
      map((response) => (Array.isArray(response) ? response : (response.content ?? []))),
      catchError(() => of([] as ZoneInfo[])),
    );
  }
}
