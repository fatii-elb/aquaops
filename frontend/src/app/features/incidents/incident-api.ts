import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { HistoryEntry, Incident, IncidentStatus, UpdateIncidentBody } from './incident.model';

/** Appels vers le service des incidents, toujours via la passerelle (chemins relatifs /api/incidents). */
@Injectable({ providedIn: 'root' })
export class IncidentApi {
  private http = inject(HttpClient);
  private readonly base = '/api/incidents';

  list(status?: IncidentStatus): Observable<Incident[]> {
    const params = status ? new HttpParams().set('status', status) : undefined;
    return this.http.get<Incident[]>(this.base, { params });
  }

  get(id: number): Observable<Incident> {
    return this.http.get<Incident>(`${this.base}/${id}`);
  }

  history(id: number): Observable<HistoryEntry[]> {
    return this.http.get<HistoryEntry[]>(`${this.base}/${id}/history`);
  }

  /** Confirmer, assigner, changer l'état ou clore : le serveur vérifie la transition. */
  update(id: number, body: UpdateIncidentBody): Observable<Incident> {
    return this.http.put<Incident>(`${this.base}/${id}`, body);
  }

  addComment(id: number, text: string, author: string): Observable<HistoryEntry> {
    return this.http.post<HistoryEntry>(`${this.base}/${id}/comments`, { text, author });
  }
}

/** Message d'erreur lisible à partir d'une réponse du serveur ({ message }). */
export function errorMessage(error: unknown, fallback: string): string {
  const message = (error as { error?: { message?: unknown } } | null)?.error?.message;
  return typeof message === 'string' && message.length > 0 ? message : fallback;
}
