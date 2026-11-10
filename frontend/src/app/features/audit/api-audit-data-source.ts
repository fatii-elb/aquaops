import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, of } from 'rxjs';
import { AuditDataSource } from './audit-data-source';
import { AUDIT_DATA_SOURCE_CONFIG } from './audit-data-source.config';
import { AuditPage, AuditQuery } from './audit.model';

/** Vraies données : GET /api/assets/audit (service des actifs, via la passerelle). */
@Injectable()
export class ApiAuditDataSource extends AuditDataSource {
  private http = inject(HttpClient);
  private baseUrl = AUDIT_DATA_SOURCE_CONFIG.baseUrl;

  search(query: AuditQuery): Observable<AuditPage> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    if (query.actor) params = params.set('actor', query.actor);
    if (query.action) params = params.set('action', query.action);
    if (query.from) params = params.set('from', query.from);
    if (query.to) params = params.set('to', query.to);
    return this.http.get<AuditPage>(this.baseUrl, { params });
  }

  /** Sans cette liste le filtre reste utilisable (aucune option), le tableau ne doit pas échouer. */
  actions(): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/actions`).pipe(catchError(() => of([])));
  }
}
