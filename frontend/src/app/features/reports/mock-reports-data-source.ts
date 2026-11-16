import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { Incident, ZoneInfo } from '../incidents/incident.model';
import { Intervention } from '../interventions/intervention.model';
import { ReportsDataSource } from './reports-data-source';
import { SAMPLE_ZONES, sampleIncidents, sampleInterventions } from './reports-sample-data';

/** Données simulées, datées par rapport à aujourd'hui : la page est utilisable sans aucun service. */
@Injectable()
export class MockReportsDataSource extends ReportsDataSource {
  incidents(): Observable<Incident[]> {
    return of(sampleIncidents(new Date()));
  }

  interventions(): Observable<Intervention[] | null> {
    return of(sampleInterventions(new Date()));
  }

  zones(): Observable<ZoneInfo[]> {
    return of(SAMPLE_ZONES);
  }
}
